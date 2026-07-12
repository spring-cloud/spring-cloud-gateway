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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * A {@link GlobalFilter} that provides WebSession-based sticky load balancing as an
 * opt-in complement to the standard {@link ReactiveLoadBalancerClientFilter}.
 *
 * <p>
 * Routes opt in to sticky routing by using {@code sticky://} as the URI scheme (e.g.
 * {@code sticky://myservice}). Routes using the normal {@code lb://} scheme are handled
 * by {@link ReactiveLoadBalancerClientFilter} and are not affected by this filter.
 *
 * <p>
 * This filter sets the exchange attribute {@link #IS_STICKY_ATTRIBUTE} to {@code true}
 * for sticky routes, then delegates instance selection to a
 * {@link WebSessionStickyLoadBalancer}. The load balancer uses the gateway's own
 * {@link org.springframework.web.server.WebSession} to pin a client to a specific service
 * instance. If the previously pinned instance is no longer available, the balancer picks
 * a new instance and updates the session affinity transparently.
 *
 * <p>
 * This filter runs at order
 * {@link ReactiveLoadBalancerClientFilter#LOAD_BALANCER_CLIENT_FILTER_ORDER}. Enable it
 * with: <pre>{@code
 * spring.cloud.gateway.global-filter.web-session-sticky-load-balancer.enabled=true
 * }</pre>
 *
 * @author Beteab Gebru
 * @since 5.0.4
 * @see WebSessionStickyLoadBalancer
 */
public class WebSessionStickyLoadBalancerFilter implements GlobalFilter, Ordered {

	/**
	 * Logger for this class.
	 */
	private static final Log LOG = LogFactory.getLog(WebSessionStickyLoadBalancerFilter.class);

	/**
	 * Filter order — matches
	 * {@link ReactiveLoadBalancerClientFilter#LOAD_BALANCER_CLIENT_FILTER_ORDER}.
	 */
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER;

	/**
	 * URI scheme that triggers WebSession-based sticky routing.
	 */
	public static final String STICKY_SCHEME = "sticky";

	/**
	 * Exchange attribute set to {@code true} when the route uses the
	 * {@link #STICKY_SCHEME} scheme. Consulted by {@link WebSessionStickyLoadBalancer} to
	 * decide whether to apply session affinity.
	 */
	public static final String IS_STICKY_ATTRIBUTE = "spring.cloud.gateway.sticky.is-sticky";

	private final LoadBalancerClientFactory clientFactory;

	/**
	 * Creates a new {@code WebSessionStickyLoadBalancerFilter}.
	 * @param clientFactory the factory used to resolve per-service load balancers
	 */
	public WebSessionStickyLoadBalancerFilter(final LoadBalancerClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
		URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);

		if (url == null || (!isStickyScheme(url.getScheme()) && !isStickyScheme(schemePrefix))) {
			return chain.filter(exchange);
		}

		boolean isSticky = isStickyScheme(url.getScheme()) || isStickyScheme(schemePrefix);
		exchange.getAttributes().put(IS_STICKY_ATTRIBUTE, isSticky);

		addOriginalRequestUrl(exchange, url);

		if (LOG.isTraceEnabled()) {
			LOG.trace(WebSessionStickyLoadBalancerFilter.class.getSimpleName() + " url before: " + url + ", sticky="
					+ isSticky);
		}

		return choose(exchange).doOnNext(response -> {
			if (!response.hasServer()) {
				throw NotFoundException.create(false, "Unable to find instance for " + url.getHost());
			}

			URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
			String overrideScheme = null;
			if (schemePrefix != null) {
				overrideScheme = url.getScheme();
			}

			DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(response.getServer(),
					overrideScheme);
			URI requestUrl = reconstructURI(serviceInstance, uri);

			if (LOG.isTraceEnabled()) {
				LOG.trace(WebSessionStickyLoadBalancerFilter.class.getSimpleName() + " url chosen: " + requestUrl);
			}
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		}).then(chain.filter(exchange));
	}

	private static boolean isStickyScheme(final String scheme) {
		return STICKY_SCHEME.equals(scheme);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Mono<Response<ServiceInstance>> choose(final ServerWebExchange exchange) {
		URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		ReactorServiceInstanceLoadBalancer loadBalancer = this.clientFactory.getInstance(uri.getHost(),
				ReactorServiceInstanceLoadBalancer.class);
		if (loadBalancer == null) {
			throw new NotFoundException("No loadbalancer available for " + uri.getHost());
		}
		return loadBalancer.choose(createRequest(exchange));
	}

	private static Request<ServerWebExchange> createRequest(final ServerWebExchange exchange) {
		return new DefaultRequest<>(exchange);
	}

	/**
	 * Reconstructs the target URI using the chosen {@link ServiceInstance}. Protected to
	 * allow overriding in subclasses or tests.
	 * @param serviceInstance the chosen instance
	 * @param original the original request URI
	 * @return the rewritten URI pointing at the chosen instance
	 */
	protected URI reconstructURI(final ServiceInstance serviceInstance, final URI original) {
		return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
	}

}
