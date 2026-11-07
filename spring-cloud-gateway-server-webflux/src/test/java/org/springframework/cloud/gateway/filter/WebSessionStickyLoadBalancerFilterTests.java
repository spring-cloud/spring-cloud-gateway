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
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * Tests for {@link WebSessionStickyLoadBalancerFilter}.
 *
 * @author Beteab Gebru
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class WebSessionStickyLoadBalancerFilterTests {

	private GatewayFilterChain chain;

	private LoadBalancerClientFactory clientFactory;

	private WebSessionStickyLoadBalancerFilter filter;

	private ServerWebExchange exchange;

	@BeforeEach
	void setUp() {
		chain = mock(GatewayFilterChain.class);
		clientFactory = mock(LoadBalancerClientFactory.class);
		filter = new WebSessionStickyLoadBalancerFilter(clientFactory);
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/path").build());
		when(chain.filter(any())).thenReturn(Mono.empty());
	}

	// -----------------------------------------------------------------------
	// Pass-through cases → non-sticky / non-lb URIs
	// -----------------------------------------------------------------------

	@Test
	void shouldPassThroughWhenGatewayRequestUrlAttrIsMissing() {
		filter.filter(exchange, chain).block();

		verify(chain).filter(exchange);
		verifyNoInteractions(clientFactory);
	}

	@Test
	void shouldPassThroughWhenSchemeIsNotSticky() {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("lb://my-service"));

		filter.filter(exchange, chain).block();

		verify(chain).filter(exchange);
		verifyNoInteractions(clientFactory);
	}

	@Test
	void shouldPassThroughWhenSchemeIsHttp() {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://my-service"));

		filter.filter(exchange, chain).block();

		verify(chain).filter(exchange);
		verifyNoInteractions(clientFactory);
	}

	@Test
	void shouldPassThroughWhenSchemePrefixAttrIsNotSticky() {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://my-service"));
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "lb");

		filter.filter(exchange, chain).block();

		verify(chain).filter(exchange);
		verifyNoInteractions(clientFactory);
	}

	// -----------------------------------------------------------------------
	// IS_STICKY_ATTRIBUTE set correctly
	// -----------------------------------------------------------------------

	@Test
	void shouldSetIsStickyTrueForStickyScheme() {
		ServiceInstance instance = new DefaultServiceInstance("s1", "my-service", "host1", 8080, false);
		mockLoadBalancer("my-service", new DefaultResponse(instance));

		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("sticky://my-service"));

		filter.filter(exchange, chain).block();

		assertThat((Boolean) exchange.getAttribute(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE)).isTrue();
	}

	@Test
	void shouldSetIsStickyTrueForStickySchemePrefix() {
		ServiceInstance instance = new DefaultServiceInstance("s1", "my-service", "host1", 8080, false);
		mockLoadBalancer("my-service", new DefaultResponse(instance));

		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://my-service"));
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "sticky");

		filter.filter(exchange, chain).block();

		assertThat((Boolean) exchange.getAttribute(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE)).isTrue();
	}

	// -----------------------------------------------------------------------
	// URI rewrite on happy path
	// -----------------------------------------------------------------------

	@Test
	void shouldRewriteRequestUriToChosenInstance() {
		ServiceInstance instance = new DefaultServiceInstance("s1", "my-service", "host1", 8080, false);
		mockLoadBalancer("my-service", new DefaultResponse(instance));

		URI stickyUri = URI.create("sticky://my-service/path");
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, stickyUri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		URI rewritten = captor.getValue().getAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(rewritten).isNotNull();
		assertThat(rewritten.getHost()).isEqualTo("host1");
		assertThat(rewritten.getPort()).isEqualTo(8080);

		// Original URL should be preserved
		assertThat((LinkedHashSet<URI>) exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(stickyUri);
	}

	// -----------------------------------------------------------------------
	// NotFoundException when no loadbalancer configured
	// -----------------------------------------------------------------------

	@Test
	void shouldThrowNotFoundExceptionWhenNoLoadBalancerAvailable() {
		when(clientFactory.getInstance(eq("my-service"), eq(ReactorServiceInstanceLoadBalancer.class)))
			.thenReturn(null);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("sticky://my-service"));

		assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> filter.filter(exchange, chain).block())
			.withMessageContaining("No loadbalancer available for my-service");
	}

	// -----------------------------------------------------------------------
	// NotFoundException when loadbalancer returns no server
	// -----------------------------------------------------------------------

	@Test
	void shouldThrowNotFoundExceptionWhenNoInstanceFound() {
		mockLoadBalancer("my-service", new EmptyResponse());
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("sticky://my-service"));

		assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> filter.filter(exchange, chain).block())
			.withMessageContaining("Unable to find instance for my-service");
	}

	// -----------------------------------------------------------------------
	// Filter order
	// -----------------------------------------------------------------------

	@Test
	void filterOrderShouldMatchReactiveLoadBalancerClientFilterOrder() {
		assertThat(filter.getOrder()).isEqualTo(ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER);
	}

	// -----------------------------------------------------------------------
	// Helper
	// -----------------------------------------------------------------------

	private void mockLoadBalancer(String serviceId, Response<ServiceInstance> response) {
		ReactorServiceInstanceLoadBalancer lb = mock(ReactorServiceInstanceLoadBalancer.class);
		when(lb.choose(any(Request.class))).thenReturn(Mono.just(response));
		when(clientFactory.getInstance(eq(serviceId), eq(ReactorServiceInstanceLoadBalancer.class))).thenReturn(lb);
	}

}
