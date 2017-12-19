/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.Collections;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class LoadBalancerClientFilterTests {

	@Test
	public void happyPath() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get?a=b")
				.build();

		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange webExchange = testFilter(request, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1")
				.hasParameter("a", "b");
	}

	@Test
	public void noQueryParams() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.build();

		ServerWebExchange webExchange = testFilter(request, URI.create("lb://service1"));
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1");
	}

	@Test
	public void encodedParameters() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode().toUri();

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		URI lbUrl = UriComponentsBuilder.fromUriString("lb://service1?a=b&c=d[]").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(lbUrl).hasParameter("c", "d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	@Test
	public void unencodedParameters() {
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		URI lbUrl = URI.create("lb://service1?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	private ServerWebExchange testFilter(MockServerHttpRequest request, URI uri) {
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);
		when(loadBalancerClient.choose("service1")).
				thenReturn(new DefaultServiceInstance("service1", "service1-host1", 8081, 
						false, Collections.emptyMap()));
		
		LoadBalancerClientFilter filter = new LoadBalancerClientFilter(loadBalancerClient);
		filter.filter(exchange, filterChain);

		return captor.getValue();
	}
}
