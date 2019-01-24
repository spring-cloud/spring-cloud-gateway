/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.rsocket.test;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.rsocket.util.RSocketProxy;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor;
import org.springframework.cloud.gateway.rsocket.Metadata;
import org.springframework.cloud.gateway.rsocket.Registry;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

@SpringBootApplication
public class PingPongApp {

	@Bean
	public Ping ping() {
		return new Ping();
	}

	@Bean
	public Pong pong() {
		return new Pong();
	}

	//TODO: move to auto-configuration
	@Bean
	public Registry registry() {
		return new Registry();
	}

	@Bean
	public GatewayRSocket gatewayRSocket(Registry registry) {
		return new GatewayRSocket(registry);
	}

	//TODO: move to auto-configuration
	@Bean
	public GatewaySocketAcceptor socketAcceptor(Registry registry, GatewayRSocket rsocket) {
		return new GatewaySocketAcceptor(registry, rsocket);
	}

	@Bean
	public GatewayApp gatewayApp(GatewaySocketAcceptor socketAcceptor, MeterRegistry meterRegistry) {
		return new GatewayApp(socketAcceptor, meterRegistry);
	}

	public static void main(String[] args) {
		SpringApplication.run(PingPongApp.class, args);
	}

	@Log4j2
	static class GatewayApp implements Ordered, ApplicationListener<ApplicationReadyEvent> {

		private final SocketAcceptor socketAcceptor;
		private final MeterRegistry meterRegistry;
		private MicrometerRSocketInterceptor interceptor;

		public GatewayApp(SocketAcceptor socketAcceptor, MeterRegistry meterRegistry) {
			this.socketAcceptor = socketAcceptor;
			this.meterRegistry = meterRegistry;
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			log.info("Starting Gateway App");
			interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag.of("component", "proxy"));
			TcpServerTransport transport = TcpServerTransport.create(7002);

			Mono<CloseableChannel> rsocketServer = RSocketFactory.receive()
					.addServerPlugin(interceptor)
					.acceptor(this.socketAcceptor)
					.transport(transport)
					.start();

			rsocketServer.subscribe();
		}
	}

	static String reply(String in) {
		if (in.length() > 4) {
			in = in.substring(0, 4);
		}
		switch (in.toLowerCase()) {
			case "ping":
				return "pong";
			case "pong":
				return "ping";
			default:
				throw new IllegalArgumentException("Value must be ping or pong, not " + in);
		}
	}

	@Log4j2
	static class Ping implements Ordered, ApplicationListener<ApplicationReadyEvent> {

		@Autowired
		MeterRegistry meterRegistry;

		private String id = "";

		public Ping() {
		}

		public Ping(String id) {
			this.id = id;
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			log.info("Starting Ping"+id);
			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag.of("component", "ping"));
			ByteBuf announcementMetadata = Metadata.encodeAnnouncement("ping");
			RSocketFactory.connect()
					.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor)
					.transport(TcpClientTransport.create(7002)) // proxy
					.start()
					.flatMapMany(socket ->
							socket.requestChannel(
									Flux.interval(Duration.ofSeconds(1))
											.map(i -> {
												ByteBuf data = ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, "ping" + id);
												ByteBuf routingMetadata = Metadata.encodeRouting("pong");
												return DefaultPayload.create(data, routingMetadata);
											})
							).map(Payload::getDataUtf8)
									.doOnNext(str -> log.info("received " + str + " in Ping"+id))
									// .take(10)
									.doFinally(signal -> socket.dispose())
					)
					.then()
					.subscribe();
		}
	}

	@Log4j2
	static class Pong implements Ordered, ApplicationListener<ApplicationReadyEvent> {

		@Autowired
		MeterRegistry meterRegistry;

		@Override
		public int getOrder() {
			return 1;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			log.info("Starting Pong");
			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag.of("component", "pong"));
			ByteBuf announcementMetadata = Metadata.encodeAnnouncement("pong");
			RSocketFactory.connect()
					.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor)
					.acceptor(this::accept)
					.transport(TcpClientTransport.create(7002)) // proxy
					.start()
					.then()
					.block();
		}

		@SuppressWarnings("Duplicates")
		RSocket accept(RSocket rSocket) {
			RSocket pong = new RSocketProxy(rSocket) {

				@Override
				public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
					return Flux.from(payloads)
							.map(Payload::getDataUtf8)
							.doOnNext(str -> log.info("received " + str + " in Pong"))
							.map(PingPongApp::reply)
							.map(reply -> {
								ByteBuf data = ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, reply);
								ByteBuf routingMetadata = Metadata.encodeRouting("ping");
								return DefaultPayload.create(data, routingMetadata);
							});
				}
			};
			return pong;
		}
	}
}
