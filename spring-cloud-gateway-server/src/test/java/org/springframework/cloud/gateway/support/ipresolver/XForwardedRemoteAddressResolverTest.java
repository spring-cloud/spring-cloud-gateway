/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.support.ipresolver;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class XForwardedRemoteAddressResolverTest {

	private final InetSocketAddress remote0000Address = InetSocketAddress.createUnresolved("0.0.0.0", 1234);

	private final XForwardedRemoteAddressResolver trustOne = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

	private final XForwardedRemoteAddressResolver trustAll = XForwardedRemoteAddressResolver.trustAll();

	@Test
	public void maxIndexOneReturnsLastForwardedIp() {
		ServerWebExchange exchange = buildExchange(oneTwoThreeBuilder());

		InetSocketAddress address = trustOne.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.3");
	}

	@Test
	public void maxIndexOneFallsBackToRemoteIp() {
		ServerWebExchange exchange = buildExchange(remoteAddressOnlyBuilder());

		InetSocketAddress address = trustOne.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");
	}

	@Test
	public void maxIndexOneReturnsNullIfNoForwardedOrRemoteIp() {
		ServerWebExchange exchange = buildExchange(emptyBuilder());

		InetSocketAddress address = trustOne.resolve(exchange);

		assertThat(address).isEqualTo(null);
	}

	@Test
	public void trustOneFallsBackOnEmptyHeader() {
		ServerWebExchange exchange = buildExchange(remoteAddressOnlyBuilder().header("X-Forwarded-For", ""));

		InetSocketAddress address = trustOne.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");

	}

	@Test
	public void trustOneFallsBackOnMultipleHeaders() {
		ServerWebExchange exchange = buildExchange(
				remoteAddressOnlyBuilder().header("X-Forwarded-For", "0.0.0.1").header("X-Forwarded-For", "0.0.0.2"));

		InetSocketAddress address = trustOne.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");
	}

	@Test
	public void trustAllReturnsFirstForwardedIp() {
		ServerWebExchange exchange = buildExchange(oneTwoThreeBuilder());

		InetSocketAddress address = trustAll.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.1");
	}

	@Test
	public void trustAllFinalFallsBackToRemoteIp() {
		ServerWebExchange exchange = buildExchange(remoteAddressOnlyBuilder());

		InetSocketAddress address = trustAll.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");
	}

	@Test
	public void trustAllReturnsNullIfNoForwardedOrRemoteIp() {
		ServerWebExchange exchange = buildExchange(emptyBuilder());

		InetSocketAddress address = trustAll.resolve(exchange);

		assertThat(address).isEqualTo(null);
	}

	@Test
	public void trustAllFallsBackOnEmptyHeader() {
		ServerWebExchange exchange = buildExchange(remoteAddressOnlyBuilder().header("X-Forwarded-For", ""));

		InetSocketAddress address = trustAll.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");

	}

	@Test
	public void trustAllFallsBackOnMultipleHeaders() {
		ServerWebExchange exchange = buildExchange(
				remoteAddressOnlyBuilder().header("X-Forwarded-For", "0.0.0.1").header("X-Forwarded-For", "0.0.0.2"));

		InetSocketAddress address = trustAll.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");
	}

	private MockServerHttpRequest.BaseBuilder emptyBuilder() {
		return MockServerHttpRequest.get("someUrl");
	}

	private MockServerHttpRequest.BaseBuilder remoteAddressOnlyBuilder() {
		return MockServerHttpRequest.get("someUrl").remoteAddress(remote0000Address);
	}

	private MockServerHttpRequest.BaseBuilder oneTwoThreeBuilder() {
		return MockServerHttpRequest.get("someUrl").remoteAddress(remote0000Address).header("X-Forwarded-For",
				"0.0.0.1, 0.0.0.2, 0.0.0.3");
	}

	private ServerWebExchange buildExchange(MockServerHttpRequest.BaseBuilder requestBuilder) {
		return MockServerWebExchange.from(requestBuilder.build());
	}

}
