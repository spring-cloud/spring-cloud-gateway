package org.springframework.cloud.gateway.support.ipresolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

public class XForwardedRemoteAddressResolverTest {

	private final InetSocketAddress remote0000Address = InetSocketAddress
			.createUnresolved("0.0.0.0", 1234);

	private final XForwardedRemoteAddressResolver trustOne = XForwardedRemoteAddressResolver
			.maxTrustedIndex(1);

	private final XForwardedRemoteAddressResolver trustAll = XForwardedRemoteAddressResolver
			.trustAll();

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
		ServerWebExchange exchange = buildExchange(
				remoteAddressOnlyBuilder().header("X-Forwarded-For", ""));

		InetSocketAddress address = trustOne.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");

	}

	@Test
	public void trustOneFallsBackOnMultipleHeaders() {
		ServerWebExchange exchange = buildExchange(
				remoteAddressOnlyBuilder().header("X-Forwarded-For", "0.0.0.1")
						.header("X-Forwarded-For", "0.0.0.2"));

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
		ServerWebExchange exchange = buildExchange(
				remoteAddressOnlyBuilder().header("X-Forwarded-For", ""));

		InetSocketAddress address = trustAll.resolve(exchange);

		assertThat(address.getHostName()).isEqualTo("0.0.0.0");

	}

	@Test
	public void trustAllFallsBackOnMultipleHeaders() {
		ServerWebExchange exchange = buildExchange(
				remoteAddressOnlyBuilder().header("X-Forwarded-For", "0.0.0.1")
						.header("X-Forwarded-For", "0.0.0.2"));

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
		return MockServerHttpRequest.get("someUrl").remoteAddress(remote0000Address)
				.header("X-Forwarded-For", "0.0.0.1, 0.0.0.2, 0.0.0.3");
	}

	private ServerWebExchange buildExchange(
			MockServerHttpRequest.BaseBuilder requestBuilder) {
		return MockServerWebExchange.from(requestBuilder.build());
	}
}