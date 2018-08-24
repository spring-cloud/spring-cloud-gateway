/*
 * Copyright 2013-2018 the original author or authors.
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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.client.loadbalancer.reactive.Request;
import org.springframework.cloud.client.loadbalancer.reactive.Response;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * @author Spencer Gibb
 * @author Tim Ysewyn
 */
public class ReactiveLoadBalancerClientFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(ReactiveLoadBalancerClientFilter.class);
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10150;

	protected final LoadBalancerClientFactory clientFactory;

	public ReactiveLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
		if (url == null || (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
			return chain.filter(exchange);
		}
		//preserve the original url
		addOriginalRequestUrl(exchange, url);

		if (log.isTraceEnabled()) {
			log.trace("ReactiveLoadBalancerClientFilter url before: " + url);
		}

		return choose(exchange).doOnNext(response -> {

			if (!response.hasServer()) {
				throw new NotFoundException("Unable to find instance for " + url.getHost());
			}

			URI uri = exchange.getRequest().getURI();

			// if the `lb:<scheme>` mechanism was used, use `<scheme>` as the default,
			// if the loadbalancer doesn't provide one.
			String overrideScheme = null;
			if (schemePrefix != null) {
				overrideScheme = url.getScheme();
			}

			DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(response.getServer(), overrideScheme);
			// URI requestUrl = clientFactory.reconstructURI(serviceInstance, uri);
			URI requestUrl = updateUri(uri, serviceInstance);

			if (log.isTraceEnabled()) {
				log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
			}
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		}).then(chain.filter(exchange));
	}

	protected Mono<Response<ServiceInstance>> choose(ServerWebExchange exchange) {
		URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		ReactorLoadBalancer<ServiceInstance> loadBalancer = this.clientFactory.getInstance(uri.getHost(), ReactorLoadBalancer.class, ServiceInstance.class);
		if (loadBalancer == null) {
			throw new NotFoundException("No loadbalancer available for " + uri.getHost());
		}
		return loadBalancer.choose(createRequest());
	}

	protected Request createRequest() {
		return ReactiveLoadBalancer.REQUEST;
	}


	private static final Map<String, String> unsecureSchemeMapping;
	static
	{
		unsecureSchemeMapping = new HashMap<>();
		unsecureSchemeMapping.put("http", "https");
		unsecureSchemeMapping.put("ws", "wss");
	}

	/**
	 * Replace the scheme to the secure variant if needed. If the {@link #unsecureSchemeMapping} map contains the uri
	 * scheme and {@link ServiceInstance#isSecure()} is true, update the scheme.
	 * This assumes the uri is already encoded to avoid double encoding.
	 *
	 * @param uri
	 * @param serviceInstance
	 * @return
	 */
	static String updateToSecureScheme(URI uri, ServiceInstance serviceInstance) {
		String scheme = uri.getScheme();

		if (StringUtils.isEmpty(scheme)) {
			scheme = "http";
		}

		if (!StringUtils.isEmpty(uri.toString())
				&& unsecureSchemeMapping.containsKey(scheme)
				&& serviceInstance.isSecure()) {
			return unsecureSchemeMapping.get(scheme);
		}
		return scheme;
	}

	static URI updateUri(URI uri, ServiceInstance serviceInstance) {
		UriComponentsBuilder builder = UriComponentsBuilder
				.fromUri(uri)
				.scheme(updateToSecureScheme(uri, serviceInstance))
				.host(serviceInstance.getHost())
				.port(serviceInstance.getPort());
		// follow up with https://jira.spring.io/browse/SPR-17039
		if (uri.getRawQuery() != null) {
			// When building the URI, UriComponentsBuilder verify the allowed characters and does not
			// support the '+' so we replace it for its equivalent '%20'.
			// See issue https://jira.spring.io/browse/SPR-10172
			builder.replaceQuery(uri.getRawQuery().replace("+", "%20"));
		}
		return builder.build(true).toUri();
	}
}
