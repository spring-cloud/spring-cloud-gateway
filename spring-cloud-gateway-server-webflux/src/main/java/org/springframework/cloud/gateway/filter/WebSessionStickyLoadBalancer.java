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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.web.server.ServerWebExchange;

/**
 * A {@link ReactorServiceInstanceLoadBalancer} that implements server-side,
 * WebSession-based sticky routing. When the exchange attribute
 * {@link WebSessionStickyLoadBalancerFilter#IS_STICKY_ATTRIBUTE} is {@code true}, the
 * balancer reads a {@code serviceId -> instanceId} affinity map from the current
 * {@link org.springframework.web.server.WebSession} and re-uses the previously selected
 * instance if it is still available. If no affinity is recorded, or the recorded instance
 * has been deregistered, a fresh instance is selected via the delegate (round-robin by
 * default) and the new affinity is written back to the session.
 *
 * <p>
 * When the {@code IS_STICKY_ATTRIBUTE} is absent or {@code false}, or when the request
 * context is not a {@link ServerWebExchange} (e.g. an internal {@code WebClient} call),
 * this balancer transparently delegates to the round-robin balancer so that non-sticky
 * routes are unaffected.
 *
 * @author Beteab Gebru
 * @since 5.0.4
 * @see WebSessionStickyLoadBalancerFilter
 */
public class WebSessionStickyLoadBalancer implements ReactorServiceInstanceLoadBalancer {

	/**
	 * Logger for this class.
	 */
	private static final Log LOG = LogFactory.getLog(WebSessionStickyLoadBalancer.class);

	/**
	 * Session attribute key under which the {@code serviceId -> instanceId} affinity map
	 * is stored.
	 */
	public static final String STICKY_MAP_SESSION_ATTR = "spring.cloud.gateway.sticky.service-map";

	private final String serviceId;

	private final ReactorServiceInstanceLoadBalancer delegate;

	private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

	/**
	 * Creates a {@code WebSessionStickyLoadBalancer} backed by a
	 * {@link RoundRobinLoadBalancer} delegate.
	 * @param supplierProvider provider of available service instances
	 * @param serviceId the service identifier used to key session affinity
	 */
	public WebSessionStickyLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider,
			String serviceId) {
		this(supplierProvider, serviceId, new RoundRobinLoadBalancer(supplierProvider, serviceId));
	}

	/**
	 * Creates a {@code WebSessionStickyLoadBalancer} with a custom delegate.
	 * @param supplierProvider provider of available service instances
	 * @param serviceId the service identifier used to key session affinity
	 * @param delegate the fallback balancer used when stickiness does not apply
	 */
	public WebSessionStickyLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider, String serviceId,
			ReactorServiceInstanceLoadBalancer delegate) {
		this.serviceId = serviceId;
		this.serviceInstanceListSupplierProvider = supplierProvider;
		this.delegate = delegate;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Mono<Response<ServiceInstance>> choose(final Request request) {
		ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
			.getIfAvailable(NoopServiceInstanceListSupplier::new);
		Object context = request.getContext();

		// If the context is not a ServerWebExchange we have no session to pin
		// against — this happens for internal WebClient calls that originate
		// within the gateway itself rather than from a routed inbound request.
		if (!(context instanceof ServerWebExchange)) {
			return delegate.choose(request);
		}

		ServerWebExchange exchange = (ServerWebExchange) context;
		return supplier.get().next().flatMap(instances -> getInstanceResponse(instances, exchange, request));
	}

	@SuppressWarnings("rawtypes")
	private Mono<Response<ServiceInstance>> getInstanceResponse(final List<ServiceInstance> instances,
			final ServerWebExchange exchange, final Request request) {
		if (instances.isEmpty()) {
			LOG.warn("No servers available for service: " + this.serviceId);
			return Mono.just(new EmptyResponse());
		}

		Boolean isSticky = exchange.getAttributeOrDefault(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE,
				Boolean.FALSE);
		if (!isSticky) {
			return delegate.choose(request);
		}

		return serviceInstanceFromSession(exchange, instances).flatMap(instance -> {
			Response<ServiceInstance> response = new DefaultResponse(instance);
			return writeSessionAffinity(exchange, response);
		})
			.switchIfEmpty(Mono
				.defer(() -> delegate.choose(request).flatMap(response -> writeSessionAffinity(exchange, response))));
	}

	private Mono<ServiceInstance> serviceInstanceFromSession(final ServerWebExchange exchange,
			final List<ServiceInstance> instances) {
		return exchange.getSession().flatMap(session -> {
			@SuppressWarnings("unchecked")
			Map<String, String> stickyMap = (Map<String, String>) session.getAttribute(STICKY_MAP_SESSION_ATTR);
			if (stickyMap == null || !stickyMap.containsKey(serviceId)) {
				LOG.debug("No existing session affinity for service '" + serviceId + "', selecting new instance");
				return Mono.empty();
			}
			String pinnedInstanceId = stickyMap.get(serviceId);
			Optional<ServiceInstance> match = instances.stream()
				.filter(i -> Objects.equals(i.getInstanceId(), pinnedInstanceId))
				.findFirst();
			if (!match.isPresent()) {
				LOG.debug("Pinned instance '" + pinnedInstanceId + "' for service '" + serviceId
						+ "' is no longer available, selecting new instance");
				return Mono.empty();
			}
			LOG.debug("Reusing pinned instance '" + pinnedInstanceId + "' for service '" + serviceId + "'");
			return Mono.just(match.get());
		});
	}

	@SuppressWarnings("unchecked")
	private Mono<Response<ServiceInstance>> writeSessionAffinity(final ServerWebExchange exchange,
			final Response<ServiceInstance> response) {
		if (!response.hasServer()) {
			return Mono.just(response);
		}
		return exchange.getSession().map(session -> {
			Map<String, String> stickyMap = (Map<String, String>) session.getAttribute(STICKY_MAP_SESSION_ATTR);
			if (stickyMap == null) {
				stickyMap = new HashMap<>();
				session.getAttributes().put(STICKY_MAP_SESSION_ATTR, stickyMap);
			}
			stickyMap.put(serviceId, response.getServer().getInstanceId());
			return response;
		});
	}

}
