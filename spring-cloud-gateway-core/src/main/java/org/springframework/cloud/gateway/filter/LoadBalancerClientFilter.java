/*
 * Copyright 2013-2019 the original author or authors.
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
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.config.LoadBalancerProperties;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * @deprecated in favour of {@link ReactiveLoadBalancerClientFilter}
 * @author Spencer Gibb
 * @author Tim Ysewyn
 */
@Deprecated
public class LoadBalancerClientFilter implements GlobalFilter, Ordered {

	/**
	 * Filter order for {@link LoadBalancerClientFilter}.
	 */
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10100;

	private static final Log log = LogFactory.getLog(LoadBalancerClientFilter.class);

	protected final LoadBalancerClient loadBalancer;

	private LoadBalancerProperties properties;

	public LoadBalancerClientFilter(LoadBalancerClient loadBalancer,
			LoadBalancerProperties properties) {
		this.loadBalancer = loadBalancer;
		this.properties = properties;
	}

	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	@Override
	@SuppressWarnings("Duplicates")
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
		if (url == null
				|| (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
			return chain.filter(exchange);
		}
		// preserve the original url
		addOriginalRequestUrl(exchange, url);

		if (log.isTraceEnabled()) {
			log.trace("LoadBalancerClientFilter url before: " + url);
		}

		final ServiceInstance instance = choose(exchange);

		if (instance == null) {
			throw NotFoundException.create(properties.isUse404(),
					"Unable to find instance for " + url.getHost());
		}

		URI uri = exchange.getRequest().getURI();

		// if the `lb:<scheme>` mechanism was used, use `<scheme>` as the default,
		// if the loadbalancer doesn't provide one.
		String overrideScheme = instance.isSecure() ? "https" : "http";
		if (schemePrefix != null) {
			overrideScheme = url.getScheme();
		}

		URI requestUrl = loadBalancer.reconstructURI(
				new DelegatingServiceInstance(instance, overrideScheme), uri);

		if (log.isTraceEnabled()) {
			log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
		}

		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		return chain.filter(exchange);
	}

	protected ServiceInstance choose(ServerWebExchange exchange) {
		return loadBalancer.choose(
				((URI) exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)).getHost());
	}

}
