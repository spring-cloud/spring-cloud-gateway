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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.PoolArenaMetric;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Seungbin Ko
 */
public class NettyRoutingFilterCancelledExchangeTests {

	private static final String BODY = "u".repeat(4096);

	private DisposableServer upstream;

	private PooledByteBufAllocator allocator;

	private NettyRoutingFilter routingFilter;

	private NettyWriteResponseFilter writeResponseFilter;

	@BeforeEach
	public void before() {
		upstream = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes.get("/", (request, response) -> response.sendString(Mono.just(BODY))))
			.bindNow();

		allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 9, 0, 0, true);
		HttpClient httpClient = HttpClient.create(ConnectionProvider.create("cancelled-exchange", 1))
			.option(ChannelOption.ALLOCATOR, allocator);

		ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider = mock(ObjectProvider.class);
		when(headersFiltersProvider.getIfAvailable()).thenReturn(List.of());
		routingFilter = new NettyRoutingFilter(httpClient, headersFiltersProvider, new HttpClientProperties());
		writeResponseFilter = new NettyWriteResponseFilter(List.of(), headersFiltersProvider);
	}

	@AfterEach
	public void after() {
		upstream.disposeNow();
	}

	@Test
	public void cancelledExchangeReleasesUpstreamResponse() throws InterruptedException {
		AtomicReference<Connection> connection = new AtomicReference<>();
		CountDownLatch cancelled = new CountDownLatch(1);
		BaseSubscriber<Void> subscriber = new BaseSubscriber<>() {
		};

		// Cancel while the callback is running but before it stores the connection, so
		// the cleanup in NettyWriteResponseFilter finds nothing to dispose.
		Map<String, Object> attributes = new ConcurrentHashMap<>() {
			@Override
			public Object put(String key, Object value) {
				Object previous = super.put(key, value);
				if (CLIENT_RESPONSE_ATTR.equals(key)) {
					subscriber.cancel();
				}
				if (CLIENT_RESPONSE_CONN_ATTR.equals(key)) {
					connection.set((Connection) value);
					cancelled.countDown();
				}
				return previous;
			}
		};
		URI uri = URI.create("http://127.0.0.1:" + upstream.port() + "/");
		attributes.put(GATEWAY_REQUEST_URL_ATTR, uri);
		attributes.put(GATEWAY_ROUTE_ATTR, Route.async().id("test").uri(uri).predicate(exchange -> true).build());

		ServerWebExchange exchange = new ServerWebExchangeDecorator(
				MockServerWebExchange.from(MockServerHttpRequest.get("/").build())) {
			@Override
			public Map<String, Object> getAttributes() {
				return attributes;
			}
		};

		writeResponseFilter.filter(exchange, ex -> routingFilter.filter(ex, unused -> Mono.empty()))
			.subscribe(subscriber);

		assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(connection.get()).isNotNull();

		Mono.delay(Duration.ofMillis(500)).block();
		assertThat(activeAllocations()).isZero();
	}

	private long activeAllocations() {
		long active = 0;
		for (PoolArenaMetric arena : allocator.metric().directArenas()) {
			active += arena.numActiveAllocations();
		}
		for (PoolArenaMetric arena : allocator.metric().heapArenas()) {
			active += arena.numActiveAllocations();
		}
		return active;
	}

}
