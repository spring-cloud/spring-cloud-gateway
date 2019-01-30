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

import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.GatewayRSocket.GatewayExchange;
import org.springframework.cloud.gateway.rsocket.GatewayRSocket.GatewayFilter;
import org.springframework.cloud.gateway.rsocket.GatewayRSocket.GatewayFilterChain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rossen Stoyanchev
 */
public class GatewayRSocketTests {

	private static Log logger = LogFactory.getLog(GatewayRSocketTests.class);

	private Registry registry;
	private Payload incomingPayload;
	private RSocket rSocket;

	@Before
	public void init() {
		registry = mock(Registry.class);
		incomingPayload = DefaultPayload.create(Unpooled.EMPTY_BUFFER,
				Metadata.encodeRouting("mock"));

		rSocket = mock(RSocket.class);
		when(registry.getRegistered(anyList())).thenReturn(rSocket);

		when(rSocket.requestResponse(any(Payload.class)))
				.thenReturn(Mono.just(DefaultPayload.create("response")));
	}


	@Test
	public void multipleFilters()  {
		TestFilter filter1 = new TestFilter();
		TestFilter filter2 = new TestFilter();
		TestFilter filter3 = new TestFilter();

		Payload payload = new GatewayRSocket(registry, Arrays.asList(filter1, filter2, filter3))
				.requestResponse(incomingPayload)
				.block(Duration.ZERO);

		assertTrue(filter1.invoked());
		assertTrue(filter2.invoked());
		assertTrue(filter3.invoked());
		assertNotNull(payload);
	}

	@Test
	public void zeroFilters()  {
		Payload payload = new GatewayRSocket(registry, Collections.emptyList())
				.requestResponse(incomingPayload)
				.block(Duration.ZERO);

		assertNotNull(payload);
	}

	@Test
	public void shortcircuitFilter()  {

		TestFilter filter1 = new TestFilter();
		ShortcircuitingFilter filter2 = new ShortcircuitingFilter();
		TestFilter filter3 = new TestFilter();


		Payload payload = new GatewayRSocket(registry, Arrays.asList(filter1, filter2, filter3))
				.requestResponse(incomingPayload)
				.block(Duration.ZERO);

		assertTrue(filter1.invoked());
		assertTrue(filter2.invoked());
		assertFalse(filter3.invoked());
		assertNull(payload);
	}

	@Test
	public void asyncFilter()  {

		AsyncFilter filter = new AsyncFilter();

		Payload payload = new GatewayRSocket(registry, Collections.singletonList(filter))
				.requestResponse(incomingPayload)
				.block(Duration.ofSeconds(5));

		assertTrue(filter.invoked());
		assertNotNull(payload);
	}

	//TODO: add exception handlers?
	@Test(expected = IllegalStateException.class)
	public void handleErrorFromFilter()  {

		ExceptionFilter filter = new ExceptionFilter();

		new GatewayRSocket(registry, Collections.singletonList(filter))
				.requestResponse(incomingPayload)
				.block(Duration.ofSeconds(5));

		// assertNull(socket);
	}


	private static class TestFilter implements GatewayFilter {

		private volatile boolean invoked;

		public boolean invoked() {
			return this.invoked;
		}

		@Override
		public Mono<Success> filter(GatewayExchange exchange, GatewayFilterChain chain) {
			this.invoked = true;
			return doFilter(exchange, chain);
		}

		public Mono<Success> doFilter(GatewayExchange exchange, GatewayFilterChain chain) {
			return chain.filter(exchange);
		}
	}


	private static class ShortcircuitingFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(GatewayExchange exchange, GatewayFilterChain chain) {
			return Mono.empty();
		}
	}

	private static class AsyncFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(GatewayExchange exchange, GatewayFilterChain chain) {
			return doAsyncWork().flatMap(asyncResult -> {
				logger.debug("Async result: " + asyncResult);
				return chain.filter(exchange);
			});
		}

		private Mono<String> doAsyncWork() {
			return Mono.delay(Duration.ofMillis(100L)).map(l -> "123");
		}
	}


	private static class ExceptionFilter implements GatewayFilter {

		@Override
		public Mono<Success> filter(GatewayExchange exchange, GatewayFilterChain chain) {
			return Mono.error(new IllegalStateException("boo"));
		}
	}


	/*private static class TestExceptionHandler implements WebExceptionHandler {

		private Throwable ex;

		@Override
		public Mono<Void> handle(GatewayExchange exchange, Throwable ex) {
			this.ex = ex;
			return Mono.error(ex);
		}
	}*/

}
