/*
 * Copyright 2013-2017 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class RouteToRequestUrlFilterTests {

	@Test
	public void happyPath() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get?a=b")
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost/mypath");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasPath("/get")
				.hasParameter("a", "b");
	}

	@Test
	public void happyPathLb() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/getb")
				.build();

		ServerWebExchange webExchange = testFilter(request, "lb://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("lb").hasHost("myhost");
	}

	@Test(expected = IllegalStateException.class)
	public void invalidHost() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/getb")
				.build();
		testFilter(request, "lb://my_host");
	}

	@Test
	public void happyPathLbPlusScheme() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/getb")
				.build();

		ServerWebExchange webExchange = testFilter(request, "lb:http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost");
		String schemePrefix = webExchange.getRequiredAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
		assertThat(schemePrefix).isEqualTo("lb");
	}

	@Test
	public void noQueryParams() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost");
	}

	@Test
	public void encodedParameters() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(url.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(url).hasParameter("c", "d[]");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	@Test
	public void encodedUrl() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/abc def/get").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(url.getRawPath()).isEqualTo("/abc%20def/get");

		assertThat(url).hasPath("/abc def/get");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost/abc%20def/get");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasPath("/abc def/get");

		// prove that it is not double encoded
		assertThat(uri.getRawPath()).isEqualTo("/abc%20def/get");
	}

	@Test
	public void unencodedParameters() {
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(url.getRawQuery()).isEqualTo("a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	@Test
	public void matcherWorks() {
		testMatcher(true,
				"lb:a123:stuff",
				"lb:abc:stuff",
				"lb:a.bc:stuff",
				"lb:a-bc:stuff",
				"lb:a+bc:stuff"
		);
		testMatcher(false,
				"lb:a",
				"lb:a123",
				"lb:123:stuff",
				"lb:a//:stuff"
		);
	}

	private void testMatcher(boolean shouldMatch, String... uris) {
		for (String s : uris) {
			URI uri = URI.create(s);
			boolean result = RouteToRequestUrlFilter.hasAnotherScheme(uri);
			assertThat(result).as("%s should match: %s", s, result).isEqualTo(shouldMatch);
		}
	}

	private ServerWebExchange testFilter(MockServerHttpRequest request, String routeUri) {
		Route value = Route.async().id("1")
				.uri(URI.create(routeUri))
				.order(0)
				.predicate(swe -> true)
				.build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, value);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		RouteToRequestUrlFilter filter = new RouteToRequestUrlFilter();
		filter.filter(exchange, filterChain);

		return captor.getValue();
	}
}
