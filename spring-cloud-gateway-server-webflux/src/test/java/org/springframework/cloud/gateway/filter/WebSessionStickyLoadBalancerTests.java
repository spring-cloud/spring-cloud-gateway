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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link WebSessionStickyLoadBalancer}.
 *
 * @author Beteab Gebru
 */
class WebSessionStickyLoadBalancerTests {

	private static final String SERVICE_ID = "test-service";

	private static final ServiceInstance INSTANCE_1 = new DefaultServiceInstance("instance-1", SERVICE_ID, "host1",
			8080, false);

	private static final ServiceInstance INSTANCE_2 = new DefaultServiceInstance("instance-2", SERVICE_ID, "host2",
			8081, false);

	private ReactorServiceInstanceLoadBalancer mockDelegate;

	private ServiceInstanceListSupplier mockSupplier;

	@SuppressWarnings("unchecked")
	private ObjectProvider<ServiceInstanceListSupplier> mockProvider;

	private WebSessionStickyLoadBalancer balancer;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		mockDelegate = mock(ReactorServiceInstanceLoadBalancer.class);
		mockSupplier = mock(ServiceInstanceListSupplier.class);
		mockProvider = mock(ObjectProvider.class);
		when(mockProvider.getIfAvailable(any())).thenReturn(mockSupplier);
		when(mockSupplier.get()).thenReturn(Flux.just(Arrays.asList(INSTANCE_1, INSTANCE_2)));

		balancer = new WebSessionStickyLoadBalancer(mockProvider, SERVICE_ID, mockDelegate);
	}

	// -----------------------------------------------------------------------
	// Non-ServerWebExchange context → delegate immediately
	// -----------------------------------------------------------------------

	@Test
	void shouldDelegateWhenContextIsNotServerWebExchange() {
		Request<RequestDataContext> request = new DefaultRequest<>(mock(RequestDataContext.class));
		Response<ServiceInstance> delegateResponse = new DefaultResponse(INSTANCE_1);
		when(mockDelegate.choose(request)).thenReturn(Mono.just(delegateResponse));

		StepVerifier.create(balancer.choose(request))
			.assertNext(r -> assertThat(r.getServer()).isEqualTo(INSTANCE_1))
			.verifyComplete();

		verify(mockDelegate).choose(request);
	}

	// -----------------------------------------------------------------------
	// IS_STICKY_ATTRIBUTE = false → delegate
	// -----------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	void shouldDelegateWhenExchangeIsNotSticky() {
		ServerWebExchange exchange = stickyExchange(false);
		Request<ServerWebExchange> request = new DefaultRequest<>(exchange);
		Response<ServiceInstance> delegateResponse = new DefaultResponse(INSTANCE_2);
		when(mockDelegate.choose(any())).thenReturn(Mono.just(delegateResponse));

		StepVerifier.create(balancer.choose(request))
			.assertNext(r -> assertThat(r.getServer()).isEqualTo(INSTANCE_2))
			.verifyComplete();

		verify(mockDelegate).choose(any());
	}

	// -----------------------------------------------------------------------
	// IS_STICKY_ATTRIBUTE = true, no prior session affinity → delegate + pin
	// -----------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	void shouldPickFreshInstanceAndPinWhenNoAffinityExists() {
		ServerWebExchange exchange = stickyExchange(true);
		Request<ServerWebExchange> request = new DefaultRequest<>(exchange);
		when(mockDelegate.choose(any())).thenReturn(Mono.just(new DefaultResponse(INSTANCE_1)));

		StepVerifier.create(balancer.choose(request))
			.assertNext(r -> assertThat(r.getServer().getInstanceId()).isEqualTo("instance-1"))
			.verifyComplete();

		// Session should now contain the pinned instanceId
		exchange.getSession().map(session -> {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) session
				.getAttribute(WebSessionStickyLoadBalancer.STICKY_MAP_SESSION_ATTR);
			assertThat(map).containsEntry(SERVICE_ID, "instance-1");
			return session;
		}).block();
	}

	// -----------------------------------------------------------------------
	// IS_STICKY_ATTRIBUTE = true, valid affinity in session → reuse pinned instance
	// -----------------------------------------------------------------------

	@Test
	void shouldReusePinnedInstanceWhenAffinityExistsAndInstanceIsAvailable() {
		ServerWebExchange exchange = stickyExchange(true);
		// Pre-seed the session with a pinned instance
		seedSessionAffinity(exchange, "instance-2");

		Request<ServerWebExchange> request = new DefaultRequest<>(exchange);

		StepVerifier.create(balancer.choose(request))
			.assertNext(r -> assertThat(r.getServer().getInstanceId()).isEqualTo("instance-2"))
			.verifyComplete();

		// Delegate should NOT have been consulted
		verify(mockDelegate, never()).choose(any());
	}

	// -----------------------------------------------------------------------
	// IS_STICKY_ATTRIBUTE = true, pinned instance gone → re-pick via delegate + repin
	// -----------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	void shouldRepickAndRepinWhenPinnedInstanceIsGone() {
		// Only INSTANCE_1 is "alive" now — supplier returns just that one
		when(mockSupplier.get()).thenReturn(Flux.just(Collections.singletonList(INSTANCE_1)));

		ServerWebExchange exchange = stickyExchange(true);
		// Session was pinned to instance-2, which is no longer in the list
		seedSessionAffinity(exchange, "instance-2");

		Request<ServerWebExchange> request = new DefaultRequest<>(exchange);
		when(mockDelegate.choose(any())).thenReturn(Mono.just(new DefaultResponse(INSTANCE_1)));

		StepVerifier.create(balancer.choose(request))
			.assertNext(r -> assertThat(r.getServer().getInstanceId()).isEqualTo("instance-1"))
			.verifyComplete();

		verify(mockDelegate).choose(any());

		// Affinity should now be updated to the new instance
		exchange.getSession().map(session -> {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) session
				.getAttribute(WebSessionStickyLoadBalancer.STICKY_MAP_SESSION_ATTR);
			assertThat(map).containsEntry(SERVICE_ID, "instance-1");
			return session;
		}).block();
	}

	// -----------------------------------------------------------------------
	// Empty instance list → EmptyResponse
	// -----------------------------------------------------------------------

	@Test
	void shouldReturnEmptyResponseWhenNoInstancesAvailable() {
		when(mockSupplier.get()).thenReturn(Flux.just(Collections.emptyList()));
		ServerWebExchange exchange = stickyExchange(true);
		Request<ServerWebExchange> request = new DefaultRequest<>(exchange);

		StepVerifier.create(balancer.choose(request))
			.assertNext(r -> assertThat(r).isInstanceOf(EmptyResponse.class))
			.verifyComplete();
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private static ServerWebExchange stickyExchange(boolean isSticky) {
		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost/test").build());
		exchange.getAttributes().put(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE, isSticky);
		return exchange;
	}

	private static void seedSessionAffinity(ServerWebExchange exchange, String instanceId) {
		exchange.getSession().map(session -> {
			Map<String, String> map = new HashMap<>();
			map.put(SERVICE_ID, instanceId);
			session.getAttributes().put(WebSessionStickyLoadBalancer.STICKY_MAP_SESSION_ATTR, map);
			return session;
		}).block();
	}

}
