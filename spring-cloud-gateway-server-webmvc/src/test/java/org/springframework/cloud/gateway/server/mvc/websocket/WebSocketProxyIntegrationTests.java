/*
 * Copyright 2013-present the original author or authors.
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
 */

package org.springframework.cloud.gateway.server.mvc.websocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.websocket;

/**
 * Integration tests for proxying WebSocket connections through routes in the same
 * application to backend endpoints under {@code /backend}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				// the default function routes would otherwise shadow the backend endpoints
				"spring.cloud.gateway.server.webmvc.function.enabled=false",
				"spring.cloud.gateway.server.webmvc.websocket.allowed-origins=https://exact.allowed.example",
				"spring.cloud.gateway.server.webmvc.websocket.allowed-origin-patterns=https://*.allowed.example",
				"spring.cloud.gateway.server.webmvc.websocket.max-message-size=256KB",
				"spring.cloud.gateway.server.webmvc.routes[0].id=ws_down",
				"spring.cloud.gateway.server.webmvc.routes[0].uri=ws://localhost:1",
				"spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/down/**",
				"spring.cloud.gateway.server.webmvc.routes[1].id=ws_lb",
				"spring.cloud.gateway.server.webmvc.routes[1].uri=lb://testservice",
				"spring.cloud.gateway.server.webmvc.routes[1].predicates[0]=Path=/lb/**",
				"spring.cloud.gateway.server.webmvc.routes[1].filters[0]=StripPrefix=1" })
public class WebSocketProxyIntegrationTests {

	private static final int LARGE_MESSAGE_SIZE = 200 * 1024;

	@LocalServerPort
	int port;

	@Autowired
	BackendCloseHandler backendCloseHandler;

	@Autowired
	PongHandler pongHandler;

	@Test
	void echo() throws Exception {
		ClientHandler client = connect("/ws/backend/echo", new WebSocketHttpHeaders());

		client.session.sendMessage(new TextMessage("hello"));

		assertThat(client.nextMessage()).isEqualTo("hello");
	}

	@Test
	void httpRouteUpgradesToWebSocket() throws Exception {
		ClientHandler client = connect("/http/backend/echo", new WebSocketHttpHeaders());

		client.session.sendMessage(new TextMessage("over http route"));

		assertThat(client.nextMessage()).isEqualTo("over http route");
	}

	@Test
	void backendSubProtocolIsNegotiated() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setSecWebSocketProtocol(List.of("echo-v1", "echo-v2"));

		ClientHandler client = connect("/ws/backend/protocol", headers);

		assertThat(client.session.getAcceptedProtocol()).isEqualTo("echo-v2");
		assertThat(client.nextMessage()).isEqualTo("echo-v2");
	}

	@Test
	void requestHeadersAreForwarded() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.add("my-header", "my-value");

		ClientHandler client = connect("/ws/backend/header", headers);

		assertThat(client.nextMessage()).isEqualTo("my-header:my-value");
	}

	@Test
	void messageSentByBackendBeforeClientHandshakeCompletesIsDelivered() throws Exception {
		// /backend/header sends as soon as the backend session opens
		ClientHandler client = connect("/ws/backend/header", new WebSocketHttpHeaders());

		assertThat(client.nextMessage()).isEqualTo("my-header:null");
	}

	@Test
	void largeMessageWithinConfiguredLimit() throws Exception {
		ClientHandler client = connect("/ws/backend/echo", new WebSocketHttpHeaders());
		String large = "x".repeat(LARGE_MESSAGE_SIZE);

		client.session.sendMessage(new TextMessage(large));

		assertThat(client.nextMessage()).isEqualTo(large);
	}

	@Test
	void backendCloseStatusIsRelayed() throws Exception {
		ClientHandler client = connect("/ws/backend/close", new WebSocketHttpHeaders());

		CloseStatus status = client.closeStatus.poll(5, TimeUnit.SECONDS);

		assertThat(status).isNotNull();
		assertThat(status.getCode()).isEqualTo(4999);
		assertThat(status.getReason()).isEqualTo("backend-close");
	}

	@Test
	void clientCloseStatusIsRelayed() throws Exception {
		ClientHandler client = connect("/ws/backend/client-close", new WebSocketHttpHeaders());

		client.session.close(new CloseStatus(4998, "client-close"));

		CloseStatus status = backendCloseHandler.closeStatus.poll(5, TimeUnit.SECONDS);
		assertThat(status).isNotNull();
		assertThat(status.getCode()).isEqualTo(4998);
		assertThat(status.getReason()).isEqualTo("client-close");
	}

	@Test
	void loadBalancedRouteUpgradesToWebSocket() throws Exception {
		ClientHandler client = connect("/lb/backend/echo", new WebSocketHttpHeaders());

		client.session.sendMessage(new TextMessage("load balanced"));

		assertThat(client.nextMessage()).isEqualTo("load balanced");
	}

	@Test
	void afterFilterHeadersAreSetOnHandshakeResponse() throws Exception {
		BlockingQueue<Map<String, List<String>>> responseHeaders = new LinkedBlockingQueue<>();
		ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
			.configurator(new ClientEndpointConfig.Configurator() {
				@Override
				public void afterResponse(HandshakeResponse response) {
					responseHeaders.add(response.getHeaders());
				}
			})
			.build();
		Endpoint endpoint = new Endpoint() {
			@Override
			public void onOpen(Session session, EndpointConfig endpointConfig) {
			}
		};

		try (Session session = ContainerProvider.getWebSocketContainer()
			.connectToServer(endpoint, config, URI.create("ws://localhost:" + port + "/headers/backend/echo"))) {
			assertThat(session.isOpen()).isTrue();
		}

		Map<String, List<String>> headers = responseHeaders.poll(5, TimeUnit.SECONDS);
		assertThat(headers).isNotNull();
		assertThat(headers.entrySet()).anySatisfy(header -> {
			assertThat(header.getKey()).isEqualToIgnoringCase("X-Proxied");
			assertThat(header.getValue()).containsExactly("websocket");
		});
	}

	@Test
	void pongsAreRelayedBothWays() throws Exception {
		ClientHandler client = connect("/ws/backend/pong", new WebSocketHttpHeaders());

		// the backend sends a pong as soon as it is connected
		assertThat(client.nextPong()).isEqualTo("from-backend");

		client.session.sendMessage(new PongMessage(ByteBuffer.wrap("from-client".getBytes(StandardCharsets.UTF_8))));

		assertThat(pongHandler.nextPong()).isEqualTo("from-client");
	}

	@Test
	void pingIsAnsweredByGateway() throws Exception {
		ClientHandler client = connect("/ws/backend/echo", new WebSocketHttpHeaders());

		client.session.sendMessage(new PingMessage(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8))));

		assertThat(client.nextPong()).isEqualTo("ping");
	}

	@Test
	void sameOriginHandshakeAccepted() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin("http://localhost:" + port);

		ClientHandler client = connect("/ws/backend/echo", headers);
		client.session.sendMessage(new TextMessage("same origin"));

		assertThat(client.nextMessage()).isEqualTo("same origin");
	}

	@Test
	void allowedOriginHandshakeAccepted() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin("https://app.allowed.example");

		ClientHandler client = connect("/ws/backend/echo", headers);
		client.session.sendMessage(new TextMessage("allowed origin"));

		assertThat(client.nextMessage()).isEqualTo("allowed origin");
	}

	@Test
	void exactAllowedOriginHandshakeAccepted() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin("https://exact.allowed.example");

		ClientHandler client = connect("/ws/backend/echo", headers);
		client.session.sendMessage(new TextMessage("exact origin"));

		assertThat(client.nextMessage()).isEqualTo("exact origin");
	}

	@Test
	void crossOriginHandshakeRejected() {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin("https://evil.example");

		assertThatThrownBy(() -> connect("/ws/backend/echo", headers)).hasStackTraceContaining("403");
	}

	@Test
	void unreachableBackendReturnsBadGateway() {
		assertThatThrownBy(() -> connect("/down/backend/echo", new WebSocketHttpHeaders()))
			.hasStackTraceContaining("502");
	}

	private ClientHandler connect(String path, WebSocketHttpHeaders headers) throws Exception {
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		container.setDefaultMaxTextMessageBufferSize(LARGE_MESSAGE_SIZE * 2);
		ClientHandler handler = new ClientHandler();
		handler.session = new StandardWebSocketClient(container)
			.execute(handler, headers, URI.create("ws://localhost:" + port + path))
			.get(5, TimeUnit.SECONDS);
		return handler;
	}

	static class ClientHandler extends TextWebSocketHandler {

		WebSocketSession session;

		final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

		final BlockingQueue<CloseStatus> closeStatus = new LinkedBlockingQueue<>();

		final BlockingQueue<String> pongs = new LinkedBlockingQueue<>();

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			messages.add(message.getPayload());
		}

		@Override
		protected void handlePongMessage(WebSocketSession session, PongMessage message) {
			pongs.add(payload(message));
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
			closeStatus.add(status);
		}

		String nextMessage() throws InterruptedException {
			return messages.poll(5, TimeUnit.SECONDS);
		}

		String nextPong() throws InterruptedException {
			return pongs.poll(5, TimeUnit.SECONDS);
		}

	}

	private static String payload(PongMessage message) {
		return StandardCharsets.UTF_8.decode(message.getPayload()).toString();
	}

	static class EchoHandler extends TextWebSocketHandler {

		@Override
		public void afterConnectionEstablished(WebSocketSession session) {
			session.setTextMessageSizeLimit(LARGE_MESSAGE_SIZE * 2);
		}

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
			session.sendMessage(message);
		}

	}

	static class ProtocolHandler extends TextWebSocketHandler implements SubProtocolCapable {

		@Override
		public List<String> getSubProtocols() {
			return List.of("echo-v2");
		}

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws Exception {
			session.sendMessage(new TextMessage(String.valueOf(session.getAcceptedProtocol())));
		}

	}

	static class HeaderHandler extends TextWebSocketHandler {

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws Exception {
			String value = session.getHandshakeHeaders().getFirst("my-header");
			session.sendMessage(new TextMessage("my-header:" + value));
		}

	}

	static class CloseHandler extends TextWebSocketHandler {

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws Exception {
			session.close(new CloseStatus(4999, "backend-close"));
		}

	}

	static class BackendCloseHandler extends TextWebSocketHandler {

		final BlockingQueue<CloseStatus> closeStatus = new LinkedBlockingQueue<>();

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
			closeStatus.add(status);
		}

	}

	static class PongHandler extends TextWebSocketHandler {

		final BlockingQueue<String> pongs = new LinkedBlockingQueue<>();

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws Exception {
			session.sendMessage(new PongMessage(ByteBuffer.wrap("from-backend".getBytes(StandardCharsets.UTF_8))));
		}

		@Override
		protected void handlePongMessage(WebSocketSession session, PongMessage message) {
			pongs.add(payload(message));
		}

		String nextPong() throws InterruptedException {
			return pongs.poll(5, TimeUnit.SECONDS);
		}

	}

	@SpringBootApplication
	@EnableWebSocket
	@LoadBalancerClient(name = "testservice", configuration = TestLoadBalancerConfig.Local.class)
	@Import(PermitAllSecurityConfiguration.class)
	static class Config implements WebSocketConfigurer {

		@Bean
		BackendCloseHandler backendCloseHandler() {
			return new BackendCloseHandler();
		}

		@Bean
		PongHandler pongHandler() {
			return new PongHandler();
		}

		@Override
		public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
			// the gateway forwards the client's Origin header, which the backend accepts
			registry.addHandler(new EchoHandler(), "/backend/echo").setAllowedOrigins("*");
			registry.addHandler(new ProtocolHandler(), "/backend/protocol").setAllowedOrigins("*");
			registry.addHandler(new HeaderHandler(), "/backend/header").setAllowedOrigins("*");
			registry.addHandler(new CloseHandler(), "/backend/close").setAllowedOrigins("*");
			registry.addHandler(backendCloseHandler(), "/backend/client-close").setAllowedOrigins("*");
			registry.addHandler(pongHandler(), "/backend/pong").setAllowedOrigins("*");
		}

		@Bean
		RouterFunction<ServerResponse> websocketRoute() {
			// @formatter:off
			return route("websocket")
					.GET("/ws/**", websocket())
					.before(stripPrefix(1))
					.before(new LocalServerPortUriResolver())
					.build();
			// @formatter:on
		}

		@Bean
		RouterFunction<ServerResponse> responseHeaderRoute() {
			// @formatter:off
			return route("response_header")
					.GET("/headers/**", websocket())
					.before(stripPrefix(1))
					.before(new LocalServerPortUriResolver())
					.after(addResponseHeader("X-Proxied", "websocket"))
					.build();
			// @formatter:on
		}

		@Bean
		RouterFunction<ServerResponse> httpRoute() {
			// @formatter:off
			return route("http")
					.GET("/http/**", http())
					.before(stripPrefix(1))
					.before(new LocalServerPortUriResolver())
					.build();
			// @formatter:on
		}

	}

}
