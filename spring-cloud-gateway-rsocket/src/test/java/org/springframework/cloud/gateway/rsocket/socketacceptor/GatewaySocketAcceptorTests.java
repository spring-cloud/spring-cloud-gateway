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

package org.springframework.cloud.gateway.rsocket.socketacceptor;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rossen Stoyanchev
 */
public class GatewaySocketAcceptorTests {

	private static Log logger = LogFactory.getLog(GatewaySocketAcceptorTests.class);

	private GatewayRSocket.Factory factory;

	private ConnectionSetupPayload setupPayload;

	private RSocket sendingSocket;

	private MeterRegistry meterRegistry;

	private GatewayRSocketProperties properties = new GatewayRSocketProperties();

	@Before
	public void init() {
		this.factory = mock(GatewayRSocket.Factory.class);
		this.setupPayload = mock(ConnectionSetupPayload.class);
		this.sendingSocket = mock(RSocket.class);
		this.meterRegistry = new SimpleMeterRegistry();

		when(this.factory.create(any(Metadata.class)))
				.thenReturn(mock(GatewayRSocket.class));
	}

	// TODO: test metrics

	@Test
	public void multipleFilters() {
		TestFilter filter1 = new TestFilter();
		TestFilter filter2 = new TestFilter();
		TestFilter filter3 = new TestFilter();

		RSocket socket = new GatewaySocketAcceptor(this.factory,
				Arrays.asList(filter1, filter2, filter3), this.meterRegistry,
				this.properties).accept(this.setupPayload, this.sendingSocket)
						.block(Duration.ZERO);

		assertThat(filter1.invoked()).isTrue();
		assertThat(filter2.invoked()).isTrue();
		assertThat(filter3.invoked()).isTrue();
		assertThat(socket).isNotNull();
	}

	@Test
	public void zeroFilters() {
		RSocket socket = new GatewaySocketAcceptor(this.factory, Collections.emptyList(),
				this.meterRegistry, this.properties)
						.accept(this.setupPayload, this.sendingSocket)
						.block(Duration.ZERO);

		assertThat(socket).isNotNull();
	}

	@Test
	public void shortcircuitFilter() {

		TestFilter filter1 = new TestFilter();
		ShortcircuitingFilter filter2 = new ShortcircuitingFilter();
		TestFilter filter3 = new TestFilter();

		RSocket socket = new GatewaySocketAcceptor(this.factory,
				Arrays.asList(filter1, filter2, filter3), this.meterRegistry,
				this.properties).accept(this.setupPayload, this.sendingSocket)
						.block(Duration.ZERO);

		assertThat(filter1.invoked()).isTrue();
		assertThat(filter2.invoked()).isTrue();
		assertThat(filter3.invoked()).isFalse();
		assertThat(socket).isNull();
	}

	@Test
	public void asyncFilter() {

		AsyncFilter filter = new AsyncFilter();

		RSocket socket = new GatewaySocketAcceptor(this.factory, singletonList(filter),
				this.meterRegistry, this.properties)
						.accept(this.setupPayload, this.sendingSocket)
						.block(Duration.ofSeconds(5));

		assertThat(filter.invoked()).isTrue();
		assertThat(socket).isNotNull();
	}

	// TODO: add exception handlers?
	@Test(expected = IllegalStateException.class)
	public void handleErrorFromFilter() {

		ExceptionFilter filter = new ExceptionFilter();

		new GatewaySocketAcceptor(this.factory, singletonList(filter), this.meterRegistry,
				this.properties).accept(this.setupPayload, this.sendingSocket)
						.block(Duration.ofSeconds(5));

	}

	private static class TestFilter implements SocketAcceptorFilter {

		private volatile boolean invoked;

		public boolean invoked() {
			return this.invoked;
		}

		@Override
		public Mono<Success> filter(SocketAcceptorExchange exchange,
				SocketAcceptorFilterChain chain) {
			this.invoked = true;
			return doFilter(exchange, chain);
		}

		public Mono<Success> doFilter(SocketAcceptorExchange exchange,
				SocketAcceptorFilterChain chain) {
			return chain.filter(exchange);
		}

	}

	private static class ShortcircuitingFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(SocketAcceptorExchange exchange,
				SocketAcceptorFilterChain chain) {
			return Mono.empty();
		}

	}

	private static class AsyncFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(SocketAcceptorExchange exchange,
				SocketAcceptorFilterChain chain) {
			return doAsyncWork().flatMap(asyncResult -> {
				logger.debug("Async result: " + asyncResult);
				return chain.filter(exchange);
			});
		}

		private Mono<String> doAsyncWork() {
			return Mono.delay(Duration.ofMillis(100L)).map(l -> "123");
		}

	}

	private static class ExceptionFilter implements SocketAcceptorFilter {

		@Override
		public Mono<Success> filter(SocketAcceptorExchange exchange,
				SocketAcceptorFilterChain chain) {
			return Mono.error(new IllegalStateException("boo"));
		}

	}

	/*
	 * private static class TestExceptionHandler implements WebExceptionHandler {
	 *
	 * private Throwable ex;
	 *
	 * @Override public Mono<Void> handle(SocketAcceptorExchange exchange, Throwable ex) {
	 * this.ex = ex; return Mono.error(ex); } }
	 */

}
