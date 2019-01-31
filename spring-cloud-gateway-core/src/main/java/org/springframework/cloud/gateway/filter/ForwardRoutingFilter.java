package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

public class ForwardRoutingFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(ForwardRoutingFilter.class);

	private final ObjectProvider<DispatcherHandler> dispatcherHandlerProvider;
	//do not use this dispatcherHandler directly, use getDispatcherHandler() instead.
	private volatile DispatcherHandler dispatcherHandler;

	public ForwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		this.dispatcherHandlerProvider = dispatcherHandlerProvider;
	}

	private DispatcherHandler getDispatcherHandler() {
		if (dispatcherHandler == null) {
			dispatcherHandler = dispatcherHandlerProvider.getIfAvailable();
		}

		return dispatcherHandler;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		//TODO: translate url?

		if (log.isTraceEnabled()) {
			log.trace("Forwarding to URI: "+requestUrl);
		}

		return this.getDispatcherHandler().handle(exchange);
	}
}
