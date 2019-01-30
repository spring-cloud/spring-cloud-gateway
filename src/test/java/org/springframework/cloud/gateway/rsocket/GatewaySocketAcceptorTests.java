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

package org.springframework.cloud.gateway.rsocket;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor.SocketAcceptorFilterChain;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor.SocketAcceptorExchange;
import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor.SocketAcceptorFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Rossen Stoyanchev
 */
public class GatewaySocketAcceptorTests {

	private static Log logger = LogFactory.getLog(GatewaySocketAcceptorTests.class);

	private RSocket proxyRSocket;
	private ConnectionSetupPayload setupPayload;
	private RSocket sendingSocket;

	@Before
	public void init() {
		proxyRSocket = mock(RSocket.class);
		setupPayload = mock(ConnectionSetupPayload.class);
		sendingSocket = mock(RSocket.class);
	}


	@Test
	public void multipleFilters()  {
		TestFilter filter1 = new TestFilter();
		TestFilter filter2 = new TestFilter();
		TestFilter filter3 = new TestFilter();

		RSocket socket = new GatewaySocketAcceptor(proxyRSocket, Arrays.asList(filter1, filter2, filter3))
				.accept(setupPayload, sendingSocket)
				.block(Duration.ZERO);

		assertTrue(filter1.invoked());
		assertTrue(filter2.invoked());
		assertTrue(filter3.invoked());
		assertNotNull(socket);
	}

	@Test
	public void zeroFilters()  {
		RSocket socket = new GatewaySocketAcceptor(proxyRSocket, Collections.emptyList())
				.accept(setupPayload, sendingSocket)
				.block(Duration.ZERO);

		assertNotNull(socket);
	}

	@Test
	public void shortcircuitFilter()  {

		TestFilter filter1 = new TestFilter();
		ShortcircuitingFilter filter2 = new ShortcircuitingFilter();
		TestFilter filter3 = new TestFilter();


		RSocket socket = new GatewaySocketAcceptor(proxyRSocket, Arrays.asList(filter1, filter2, filter3))
				.accept(setupPayload, sendingSocket)
				.block(Duration.ZERO);

		assertTrue(filter1.invoked());
		assertTrue(filter2.invoked());
		assertFalse(filter3.invoked());
		assertNull(socket);
	}

	@Test
	public void asyncFilter()  {

		AsyncFilter filter = new AsyncFilter();

		RSocket socket = new GatewaySocketAcceptor(proxyRSocket, Collections.singletonList(filter))
				.accept(setupPayload, sendingSocket)
				.block(Duration.ofSeconds(5));

		assertTrue(filter.invoked());
		assertNotNull(socket);
	}

	//TODO: add exception handlers?
	@Test(expected = IllegalStateException.class)
	public void handleErrorFromFilter()  {

		ExceptionFilter filter = new ExceptionFilter();

		new GatewaySocketAcceptor(proxyRSocket, Collections.singletonList(filter))
				.accept(setupPayload, sendingSocket)
				.block(Duration.ofSeconds(5));

		// assertNull(socket);
	}


	private static class TestFilter implements SocketAcceptorFilter {

		private volatile boolean invoked;

		public boolean invoked() {
			return this.invoked;
		}

		@Override
		public Mono<Success> filter(SocketAcceptorExchange exchange, SocketAcceptorFilterChain chain) {
			this.invoked = true;
			return doFilter(exchange, chain);
		}

		public Mono<Success> doFilter(SocketAcceptorExchange exchange, SocketAcceptorFilterChain chain) {
			return chain.filter(exchange);
		}
	}


	private static class ShortcircuitingFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(SocketAcceptorExchange exchange, SocketAcceptorFilterChain chain) {
			return Mono.empty();
		}
	}

	private static class AsyncFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(SocketAcceptorExchange exchange, SocketAcceptorFilterChain chain) {
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
		public Mono<Success> filter(SocketAcceptorExchange exchange, SocketAcceptorFilterChain chain) {
			return Mono.error(new IllegalStateException("boo"));
		}
	}


	/*private static class TestExceptionHandler implements WebExceptionHandler {

		private Throwable ex;

		@Override
		public Mono<Void> handle(SocketAcceptorExchange exchange, Throwable ex) {
			this.ex = ex;
			return Mono.error(ex);
		}
	}*/

}
