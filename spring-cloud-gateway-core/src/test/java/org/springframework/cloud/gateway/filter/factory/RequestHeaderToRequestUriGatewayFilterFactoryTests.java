package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Toshiaki Maki
 */
public class RequestHeaderToRequestUriGatewayFilterFactoryTests {

	@Test
	public void filterChangeRequestUri() throws Exception {
		RequestHeaderToRequestUriGatewayFilterFactory factory = new RequestHeaderToRequestUriGatewayFilterFactory();
		GatewayFilter filter = factory.apply(c -> c.setName("X-CF-Forwarded-Url"));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
				.header("X-CF-Forwarded-Url", "https://example.com").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,
				URI.create("http://localhost"));
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
				.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());
		filter.filter(exchange, filterChain);
		ServerWebExchange webExchange = captor.getValue();
		URI uri = (URI) webExchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).isNotNull();
		assertThat(uri.toString()).isEqualTo("https://example.com");
	}

	@Test
	public void filterDoesNotChangeRequestUriIfHeaderIsAbsent() throws Exception {
		RequestHeaderToRequestUriGatewayFilterFactory factory = new RequestHeaderToRequestUriGatewayFilterFactory();
		GatewayFilter filter = factory.apply(c -> c.setName("X-CF-Forwarded-Url"));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
				.build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,
				URI.create("http://localhost"));
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
				.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());
		filter.filter(exchange, filterChain);
		ServerWebExchange webExchange = captor.getValue();
		URI uri = (URI) webExchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).isNotNull();
		assertThat(uri.toString()).isEqualTo("http://localhost");
	}

	@Test
	public void filterDoesNotChangeRequestUriIfHeaderIsInvalid() throws Exception {
		RequestHeaderToRequestUriGatewayFilterFactory factory = new RequestHeaderToRequestUriGatewayFilterFactory();
		GatewayFilter filter = factory.apply(c -> c.setName("X-CF-Forwarded-Url"));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
				.header("X-CF-Forwarded-Url", "example").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,
				URI.create("http://localhost"));
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
				.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());
		filter.filter(exchange, filterChain);
		ServerWebExchange webExchange = captor.getValue();
		URI uri = (URI) webExchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).isNotNull();
		assertThat(uri.toURL().toString()).isEqualTo("http://localhost");
	}
}