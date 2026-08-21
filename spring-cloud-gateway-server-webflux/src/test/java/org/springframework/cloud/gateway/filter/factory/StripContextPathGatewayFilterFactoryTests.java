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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Garvit Joshi
 */
public class StripContextPathGatewayFilterFactoryTests {

	@Test
	public void stripContextPathFilterWorks() {
		MockServerHttpRequest request = request("/context/service/path", "/context");

		ServerWebExchange exchange = filterAndGetExchange(new StripContextPathGatewayFilterFactory().apply(),
				MockServerWebExchange.from(request));

		assertThat(exchange.getRequest().getURI()).hasPath("/service/path");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();

		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(requestUrl).hasScheme("http").hasHost("localhost").hasNoPort().hasPath("/service/path");
		LinkedHashSet<URI> uris = exchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(request.getURI());
	}

	@Test
	public void stripContextPathFilterToRootWorks() {
		ServerWebExchange exchange = filterAndGetExchange(new StripContextPathGatewayFilterFactory().apply(),
				MockServerWebExchange.from(request("/context", "/context")));

		assertThat(exchange.getRequest().getURI()).hasPath("/");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();
	}

	@Test
	public void stripContextPathFilterWithoutContextPathIsNoOp() {
		ServerWebExchange exchange = filterAndGetExchange(new StripContextPathGatewayFilterFactory().apply(),
				MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/service/path").build()));

		assertThat(exchange.getRequest().getURI()).hasPath("/service/path");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();
		assertThat(exchange.getAttributes()).doesNotContainKey(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
	}

	@Test
	public void stripContextPathThenStripPrefixWorks() {
		ServerWebExchange exchange = stripContextPathExchange("/context/service/blue");

		exchange = filterAndGetExchange(new StripPrefixGatewayFilterFactory().apply(c -> c.setParts(1)), exchange);

		assertThat(exchange.getRequest().getURI()).hasPath("/blue");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();
	}

	@Test
	public void stripContextPathThenRewritePathWorks() {
		ServerWebExchange exchange = stripContextPathExchange("/context/service/blue");

		exchange = filterAndGetExchange(new RewritePathGatewayFilterFactory()
			.apply(c -> c.setRegexp("/service/(?<remaining>.*)").setReplacement("/${remaining}")), exchange);

		assertThat(exchange.getRequest().getURI()).hasPath("/blue");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();
	}

	@Test
	public void stripContextPathThenPrefixPathWorks() {
		ServerWebExchange exchange = stripContextPathExchange("/context/get");

		exchange = filterAndGetExchange(new PrefixPathGatewayFilterFactory().apply(c -> c.setPrefix("/prefix")),
				exchange);

		assertThat(exchange.getRequest().getURI()).hasPath("/prefix/get");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();
	}

	@Test
	public void stripContextPathThenSetPathWorks() {
		ServerWebExchange exchange = stripContextPathExchange("/context/service/blue");

		exchange = filterAndGetExchange(new SetPathGatewayFilterFactory().apply(c -> c.setTemplate("/blue")), exchange);

		assertThat(exchange.getRequest().getURI()).hasPath("/blue");
		assertThat(exchange.getRequest().getPath().contextPath().value()).isEmpty();
	}

	@Test
	public void toStringFormat() {
		GatewayFilter filter = new StripContextPathGatewayFilterFactory().apply();
		assertThat(filter.toString()).contains("StripContextPath");
	}

	private ServerWebExchange stripContextPathExchange(String path) {
		return filterAndGetExchange(new StripContextPathGatewayFilterFactory().apply(),
				MockServerWebExchange.from(request(path, "/context")));
	}

	private MockServerHttpRequest request(String path, String contextPath) {
		return MockServerHttpRequest.get("http://localhost" + path).contextPath(contextPath).build();
	}

	private ServerWebExchange filterAndGetExchange(GatewayFilter filter, ServerWebExchange exchange) {
		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		return captor.getValue();
	}

}
