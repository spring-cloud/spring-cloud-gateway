/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.test.websocket;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.CloseStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.publisher.ReplayProcessor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.test.support.HttpServer;
import org.springframework.cloud.gateway.test.support.ReactorHttpServer;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.SEC_WEBSOCKET_PROTOCOL;

/**
 * Original is here {@see https://github.com/spring-projects/spring-framework/blob/master/spring-webflux/src/test/java/org/springframework/web/reactive/socket/WebSocketIntegrationTests.java}
 * Integration tests with server-side {@link WebSocketHandler}s.
 *
 * @author Rossen Stoyanchev
 */
public class WebSocketIntegrationTests {

	private static final Log logger = LogFactory.getLog(WebSocketIntegrationTests.class);

	private WebSocketClient client;

	private HttpServer server;

	protected int serverPort;
	private ConfigurableApplicationContext gatewayContext;
	private int gatewayPort;

	@Before
	public void setup() throws Exception {
		this.client = new ReactorNettyWebSocketClient();

		this.server = new ReactorHttpServer();
		this.server.setHandler(createHttpHandler());
		this.server.afterPropertiesSet();
		this.server.start();

		// Set dynamically chosen port
		this.serverPort = this.server.getPort();

		if (this.client instanceof Lifecycle) {
			((Lifecycle) this.client).start();
		}

		this.gatewayContext = new SpringApplicationBuilder(GatewayConfig.class)
				.properties("ws.server.port:"+this.serverPort, "server.port=0", "spring.jmx.enabled=false")
				.run();

		ConfigurableEnvironment env = this.gatewayContext.getBean(ConfigurableEnvironment.class);
		this.gatewayPort = new Integer(env.getProperty("local.server.port"));
	}

	@After
	public void stop() throws Exception {
		if (this.client instanceof Lifecycle) {
			((Lifecycle) this.client).stop();
		}
		this.server.stop();
		if (this.gatewayContext != null) {
			this.gatewayContext.stop();
		}
	}

	private HttpHandler createHttpHandler() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(WebSocketTestConfig.class);
		context.refresh();
		return WebHttpHandlerBuilder.applicationContext(context).build();
	}

	protected URI getUrl(String path) throws URISyntaxException {
		// return new URI("ws://localhost:" + this.serverPort + path);
		 return new URI("ws://localhost:" + this.gatewayPort + path);
	}

	protected URI getHttpUrl(String path) throws URISyntaxException {
		return new URI("http://localhost:" + this.gatewayPort + path);
	}

	@Configuration
	static class WebSocketTestConfig {

		@Bean
		public DispatcherHandler webHandler() {
			return new DispatcherHandler();
		}

		@Bean
		public WebSocketHandlerAdapter handlerAdapter() {
			return new WebSocketHandlerAdapter(webSocketService());
		}

		@Bean
		public WebSocketService webSocketService() {
			return new HandshakeWebSocketService(getUpgradeStrategy());
		}

		protected RequestUpgradeStrategy getUpgradeStrategy() {
			return new ReactorNettyRequestUpgradeStrategy();
		}

		@Bean
		public HandlerMapping handlerMapping() {
			Map<String, WebSocketHandler> map = new HashMap<>();
			map.put("/echo", new EchoWebSocketHandler());
			map.put("/echoForHttp", new EchoWebSocketHandler());
			map.put("/sub-protocol", new SubProtocolWebSocketHandler());
			map.put("/custom-header", new CustomHeaderHandler());
			map.put("/close", new SessionClosingHandler());

			SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
			mapping.setUrlMap(map);
			return mapping;
		}
	}

	@Test
	public void echo() throws Exception {
		int count = 100;
		Flux<String> input = Flux.range(1, count).map(index -> "msg-" + index);
		ReplayProcessor<Object> output = ReplayProcessor.create(count);

		client.execute(getUrl("/echo"),
				session -> {
					logger.debug("Starting to send messages");
					return session
							.send(input.doOnNext(s -> logger.debug("outbound " + s)).map(session::textMessage))
							.thenMany(session.receive().take(count).map(WebSocketMessage::getPayloadAsText))
							.subscribeWith(output)
							.doOnNext(s -> logger.debug("inbound " + s))
							.then()
							.doOnSuccessOrError((aVoid, ex) ->
									logger.debug("Done with " + (ex != null ? ex.getMessage() : "success")));
				})
				.block(Duration.ofMillis(5000));

		assertEquals(input.collectList().block(Duration.ofMillis(5000)),
				output.collectList().block(Duration.ofMillis(5000)));
	}

	@Test
	public void echoForHttp() throws Exception {
		int count = 100;
		Flux<String> input = Flux.range(1, count).map(index -> "msg-" + index);
		ReplayProcessor<Object> output = ReplayProcessor.create(count);

		client.execute(getHttpUrl("/echoForHttp"),
				session -> {
					logger.debug("Starting to send messages");
					return session
							.send(input.doOnNext(s -> logger.debug("outbound " + s)).map(session::textMessage))
							.thenMany(session.receive().take(count).map(WebSocketMessage::getPayloadAsText))
							.subscribeWith(output)
							.doOnNext(s -> logger.debug("inbound " + s))
							.then()
							.doOnSuccessOrError((aVoid, ex) ->
									logger.debug("Done with " + (ex != null ? ex.getMessage() : "success")));
				})
				.block(Duration.ofMillis(5000));

		assertEquals(input.collectList().block(Duration.ofMillis(5000)),
				output.collectList().block(Duration.ofMillis(5000)));
	}


	@Test
	public void subProtocol() throws Exception {
		String protocol = "echo-v1";
		String protocol2 = "echo-v2";
		AtomicReference<HandshakeInfo> infoRef = new AtomicReference<>();
		MonoProcessor<Object> output = MonoProcessor.create();

		client.execute(getUrl("/sub-protocol"),
				new WebSocketHandler() {
					@Override
					public List<String> getSubProtocols() {
						return Arrays.asList(protocol, protocol2);
					}
					@Override
					public Mono<Void> handle(WebSocketSession session) {
						infoRef.set(session.getHandshakeInfo());
						return session.receive()
								.map(WebSocketMessage::getPayloadAsText)
								.subscribeWith(output)
								.then();
					}
				})
				.block(Duration.ofMillis(5000));

		HandshakeInfo info = infoRef.get();
		assertThat(info.getHeaders().getFirst("Upgrade"))
				.isEqualToIgnoringCase("websocket");

		assertThat(info.getHeaders().getFirst("Sec-WebSocket-Protocol"))
				.isEqualTo(protocol);
		assertThat(info.getSubProtocol())
				.as("Wrong protocol accepted")
				.isEqualTo(protocol);
		assertThat(output.block(Duration.ofSeconds(5)))
                .as("Wrong protocol detected on the server side")
				.isEqualTo(protocol);
	}

	@Test
	public void customHeader() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.add("my-header", "my-value");
		MonoProcessor<Object> output = MonoProcessor.create();

		client.execute(getUrl("/custom-header"), headers,
				session -> session.receive()
						.map(WebSocketMessage::getPayloadAsText)
						.subscribeWith(output)
						.then())
				.block(Duration.ofMillis(5000));

		assertEquals("my-header:my-value", output.block(Duration.ofMillis(5000)));
	}

	@Test
	public void sessionClosing() throws Exception {
		this.client.execute(getUrl("/close"),
				session -> {
					logger.debug("Starting..");
					return session.receive()
							.doOnNext(s -> logger.debug("inbound " + s))
							.then()
							.doFinally(signalType -> {
								logger.debug("Completed with: " + signalType);
							});
				})
				.block(Duration.ofMillis(5000));
	}

	private static class EchoWebSocketHandler implements WebSocketHandler {

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			// Use retain() for Reactor Netty
			return session.send(session.receive().doOnNext(WebSocketMessage::retain));
		}
	}

	private static class SubProtocolWebSocketHandler implements WebSocketHandler {

		@Override
		public List<String> getSubProtocols() {
			return Arrays.asList("echo-v1", "echo-v2");
		}

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			String protocol = session.getHandshakeInfo().getSubProtocol();
			if (!StringUtils.hasText(protocol)) {
				return Mono.error(new IllegalStateException("Missing protocol"));
			}
			List<String> protocols = session.getHandshakeInfo().getHeaders().get(SEC_WEBSOCKET_PROTOCOL);
			assertThat(protocols).contains("echo-v1,echo-v2");
			WebSocketMessage message = session.textMessage(protocol);
			return doSend(session, Mono.just(message));
		}
	}

	private static class CustomHeaderHandler implements WebSocketHandler {

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			HttpHeaders headers = session.getHandshakeInfo().getHeaders();
			if (!headers.containsKey("my-header")) {
				return Mono.error(new IllegalStateException("Missing my-header"));
			}
			String payload = "my-header:" + headers.getFirst("my-header");
			WebSocketMessage message = session.textMessage(payload);
			return doSend(session, Mono.just(message));
		}
	}

	private static class SessionClosingHandler implements WebSocketHandler {

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			return Flux.never().mergeWith(session.close(CloseStatus.GOING_AWAY)).then();
		}
	}

	private static Mono<Void> doSend(WebSocketSession session, Publisher<WebSocketMessage> output) {
		return session.send(output);
		// workaround for suspected RxNetty WebSocket client issue
		// https://github.com/ReactiveX/RxNetty/issues/560
		// return session.send(Mono.delay(Duration.ofMillis(100)).thenMany(output));
	}

	@Configuration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	@RibbonClient(name = "wsservice", configuration = LocalRibbonClientConfiguration.class)
	protected static class GatewayConfig {

		@Bean
		public RouteLocator wsRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route(r->r.path("/echoForHttp")
							.uri("lb://wsservice"))
					.route(r -> r.alwaysTrue()
							.uri("lb:ws://wsservice"))
					.build();
		}
	}

	public static class LocalRibbonClientConfiguration {

		@Value("${ws.server.port}")
		private int wsPort;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("localhost", this.wsPort));
		}

	}

}
