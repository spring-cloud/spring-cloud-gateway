package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getAttribute;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class LoadBalancerClientFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(LoadBalancerClientFilter.class);
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10100;

	private final LoadBalancerClient loadBalancer;

	public LoadBalancerClientFilter(LoadBalancerClient loadBalancer) {
		this.loadBalancer = loadBalancer;
	}

	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		URI url = getAttribute(exchange, GATEWAY_REQUEST_URL_ATTR, URI.class);
		if (url == null || !url.getScheme().equals("lb")) {
			return chain.filter(exchange);
		}
		log.trace("LoadBalancerClientFilter url before: " + url);

		final ServiceInstance instance = loadBalancer.choose(url.getHost());

		URI requestUrl = UriComponentsBuilder.fromUri(url)
				.scheme(instance.isSecure()? "https" : "http") //TODO: support websockets
				.host(instance.getHost())
				.port(instance.getPort())
				.build(true)
				.toUri();
		log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		return chain.filter(exchange);
	}

}
