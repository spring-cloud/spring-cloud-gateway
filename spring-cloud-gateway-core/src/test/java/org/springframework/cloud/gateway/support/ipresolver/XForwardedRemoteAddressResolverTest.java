package org.springframework.cloud.gateway.support.ipresolver;

import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class XForwardedRemoteAddressResolverTest {


	private final InetSocketAddress remote0000Address = InetSocketAddress.createUnresolved("0.0.0.0", 1234);

	XForwardedRemoteAddressResolver resolver = new XForwardedRemoteAddressResolver();

	@Test
	public void resolvesFirstForwardedAddress() {
		MockServerHttpRequest request = MockServerHttpRequest.get("someUrl")
				.remoteAddress(remote0000Address)
				.header("X-Forwarded-For", "0.0.0.1, 0.0.0.2, 0.0.0.3").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		InetSocketAddress actualIp = resolver.resolve(exchange);

		assertThat(actualIp.getHostName()).isEqualTo("0.0.0.1");
	}

	@Test
	public void fallsBackToRemoteAddress() {
		MockServerHttpRequest request = MockServerHttpRequest.get("someUrl")
				.remoteAddress(remote0000Address).build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		InetSocketAddress actualIp = resolver.resolve(exchange);

		assertThat(actualIp.getHostName()).isEqualTo("0.0.0.0");
	}

	@Test
	public void returnsNullIfNoForwardedOrRemoteAddress() {
		MockServerHttpRequest request = MockServerHttpRequest.get("someUrl").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		InetSocketAddress actualIp = resolver.resolve(exchange);

		assertThat(actualIp).isEqualTo(null);
	}
}