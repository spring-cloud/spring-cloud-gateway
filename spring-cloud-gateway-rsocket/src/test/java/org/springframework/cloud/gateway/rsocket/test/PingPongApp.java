/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import io.rsocket.util.RSocketProxy;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.server.GatewayExchange;
import org.springframework.cloud.gateway.rsocket.server.GatewayFilter;
import org.springframework.cloud.gateway.rsocket.server.GatewayFilterChain;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorExchange;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilterChain;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

@SpringBootApplication
public class PingPongApp {

	@Bean
	public Ping ping1() {
		return new Ping("1");
	}

	@Bean
	@ConditionalOnProperty("ping.two.enabled")
	public Ping ping2() {
		return new Ping("2");
	}

	@Bean
	public Pong pong() {
		return new Pong();
	}

	@Bean
	public MySocketAcceptorFilter mySocketAcceptorFilter() {
		return new MySocketAcceptorFilter();
	}

	public static void main(String[] args) {
		SpringApplication.run(PingPongApp.class, args);
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

	@Slf4j
	public static class Ping
			implements Ordered, ApplicationListener<ApplicationReadyEvent> {

		@Autowired
		private MeterRegistry meterRegistry;

		private final String id;

		private final AtomicInteger pongsReceived = new AtomicInteger();

		private Flux<String> pongFlux;

		public Ping(String id) {
			this.id = id;
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			log.info("Starting Ping" + id);
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
			Integer take = env.getProperty("ping.take", Integer.class, null);
			Integer gatewayPort = env.getProperty(
					"spring.cloud.gateway.rsocket.server.port", Integer.class, 7002);

			log.debug("ping.take: " + take);

			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(
					meterRegistry, Tag.of("component", "ping"));
			ByteBuf announcementMetadata = Metadata.from("ping").with("id", "ping" + id)
					.encode();
			pongFlux = RSocketFactory.connect()
					.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(
							DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor)
					.transport(TcpClientTransport.create(gatewayPort)) // proxy
					.start().flatMapMany(socket -> {
						Flux<String> pong = socket.requestChannel(
								Flux.interval(Duration.ofSeconds(1)).map(i -> {
									ByteBuf data = ByteBufUtil.writeUtf8(
											ByteBufAllocator.DEFAULT, "ping" + id);
									ByteBuf routingMetadata = Metadata.from("pong")
											.encode();
									return DefaultPayload.create(data, routingMetadata);
								}).onBackpressureDrop(payload -> log.debug(
										"Dropped payload " + payload.getDataUtf8())) // this
																						// is
																						// needed
																						// in
																						// case
																						// pong
																						// is
																						// not
																						// available
																						// yet
						).map(Payload::getDataUtf8).doOnNext(str -> {
							int received = pongsReceived.incrementAndGet();
							log.info("received " + str + "(" + received + ") in Ping"
									+ id);
						}).doFinally(signal -> socket.dispose());
						if (take != null) {
							return pong.take(take);
						}
						return pong;
					});

			pongFlux.subscribe();
		}

		public Flux<String> getPongFlux() {
			return pongFlux;
		}

		public int getPongsReceived() {
			return pongsReceived.get();
		}

	}

	@Slf4j
	public static class Pong
			implements Ordered, ApplicationListener<ApplicationReadyEvent> {

		@Autowired
		private MeterRegistry meterRegistry;

		private final AtomicInteger pingsReceived = new AtomicInteger();

		@Override
		public int getOrder() {
			return 1;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
			Integer pongDelay = env.getProperty("pong.delay", Integer.class, 5000);
			try {
				Thread.sleep(pongDelay);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			log.info("Starting Pong");
			Integer gatewayPort = env.getProperty(
					"spring.cloud.gateway.rsocket.server.port", Integer.class, 7002);
			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(
					meterRegistry, Tag.of("component", "pong"));
			ByteBuf announcementMetadata = Metadata.from("pong").with("id", "pong1")
					.encode();
			RSocketFactory.connect().metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(
							DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor).acceptor(this::accept)
					.transport(TcpClientTransport.create(gatewayPort)) // proxy
					.start().block();
		}

		@SuppressWarnings("Duplicates")
		RSocket accept(RSocket rSocket) {
			RSocket pong = new RSocketProxy(rSocket) {

				@Override
				public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
					return Flux.from(payloads).map(Payload::getDataUtf8).doOnNext(str -> {
						int received = pingsReceived.incrementAndGet();
						log.info("received " + str + "(" + received + ") in Pong");
					}).map(PingPongApp::reply).map(reply -> {
						ByteBuf data = ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT,
								reply);
						ByteBuf routingMetadata = Metadata.from("ping").encode();
						return DefaultPayload.create(data, routingMetadata);
					});
				}
			};
			return pong;
		}

		public int getPingsReceived() {
			return pingsReceived.get();
		}

	}

	@Slf4j
	public static class MyGatewayFilter implements GatewayFilter {

		private AtomicBoolean invoked = new AtomicBoolean(false);

		@Override
		public Mono<Success> filter(GatewayExchange exchange, GatewayFilterChain chain) {
			log.info("in custom gateway filter");
			invoked.compareAndSet(false, true);
			return chain.filter(exchange);
		}

		public boolean invoked() {
			return invoked.get();
		}

	}

	@Slf4j
	public static class MySocketAcceptorFilter implements SocketAcceptorFilter, Ordered {

		private AtomicBoolean invoked = new AtomicBoolean(false);

		@Override
		public Mono<Success> filter(SocketAcceptorExchange exchange,
				SocketAcceptorFilterChain chain) {
			log.info("in custom socket acceptor filter");
			invoked.compareAndSet(false, true);
			return chain.filter(exchange);
		}

		@Override
		public int getOrder() {
			return 0;
		}

		public boolean invoked() {
			return invoked.get();
		}

	}

}
