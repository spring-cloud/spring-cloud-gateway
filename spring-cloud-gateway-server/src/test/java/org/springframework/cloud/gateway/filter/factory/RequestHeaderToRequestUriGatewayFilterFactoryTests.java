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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory.NameConfig;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Toshiaki Maki
 */
public class RequestHeaderToRequestUriGatewayFilterFactoryTests {

	@Test
	public void filterChangeRequestUri() {
		RequestHeaderToRequestUriGatewayFilterFactory factory = new RequestHeaderToRequestUriGatewayFilterFactory();
		GatewayFilter filter = factory.apply(c -> c.setName("X-CF-Forwarded-Url"));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
				.header("X-CF-Forwarded-Url", "https://example.com").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://localhost"));
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());
		filter.filter(exchange, filterChain);
		ServerWebExchange webExchange = captor.getValue();
		URI uri = (URI) webExchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).isNotNull();
		assertThat(uri.toString()).isEqualTo("https://example.com");
	}

	@Test
	public void filterDoesNotChangeRequestUriIfHeaderIsAbsent() {
		RequestHeaderToRequestUriGatewayFilterFactory factory = new RequestHeaderToRequestUriGatewayFilterFactory();
		GatewayFilter filter = factory.apply(c -> c.setName("X-CF-Forwarded-Url"));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://localhost"));
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
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
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://localhost"));
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());
		filter.filter(exchange, filterChain);
		ServerWebExchange webExchange = captor.getValue();
		URI uri = (URI) webExchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).isNotNull();
		assertThat(uri.toURL().toString()).isEqualTo("http://localhost");
	}

	@Test
	public void toStringFormat() {
		NameConfig config = new NameConfig();
		config.setName("myname");
		GatewayFilter filter = new RequestHeaderToRequestUriGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname");
	}

}
