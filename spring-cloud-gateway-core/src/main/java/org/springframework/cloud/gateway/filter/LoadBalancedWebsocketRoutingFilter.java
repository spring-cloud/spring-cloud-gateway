package org.springframework.cloud.gateway.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * @author Tim Ysewyn
 */
public class LoadBalancedWebsocketRoutingFilter extends WebsocketRoutingFilter {

	private static final Log log = LogFactory.getLog(LoadBalancedWebsocketRoutingFilter.class);
	private static final String SCHEME_LB_SUFFIX = "+lb";
	private static final String SCHEME_WS_LB = "ws" + SCHEME_LB_SUFFIX;
	private static final String SCHEME_WSS_LB = "wss" + SCHEME_LB_SUFFIX;

	private final LoadBalancerClient loadBalancer;

	public LoadBalancedWebsocketRoutingFilter(WebSocketClient webSocketClient, LoadBalancerClient loadBalancer) {
		super(webSocketClient);
		this.loadBalancer = loadBalancer;
	}

	public LoadBalancedWebsocketRoutingFilter(WebSocketClient webSocketClient,
											  WebSocketService webSocketService,
											  LoadBalancerClient loadBalancer) {
		super(webSocketClient, webSocketService);
		this.loadBalancer = loadBalancer;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI uri = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = uri.getScheme();
		if (isAlreadyRouted(exchange) ||
				(!scheme.equals(SCHEME_WS_LB) && !scheme.equals(SCHEME_WSS_LB))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		uri = getUriFromLoadBalancer(uri, scheme);

		return this.handleWebSocketRequest(exchange, uri);
	}

	private URI getUriFromLoadBalancer(URI uri, String scheme) {
		log.trace("LoadBalancedWebsocketRoutingFilter uri before: " + uri);

		final ServiceInstance instance = loadBalancer.choose(uri.getHost());

		if (instance == null) {
			throw new NotFoundException("Unable to find instance for " + uri.getHost());
		}

		String websocketScheme = scheme.replace(SCHEME_LB_SUFFIX, "");

		// TODO remove after https://github.com/spring-cloud/spring-cloud-netflix/pull/2551 has been released
		if ("ws".equals(websocketScheme) && instance.isSecure()) {
			websocketScheme = "wss";
		}

		URI loadBalancedUri = UriComponentsBuilder.fromUri(uri)
												  .scheme(websocketScheme)
												  .build(true)
												  .toUri();

		return loadBalancer.reconstructURI(instance, loadBalancedUri);
	}
}
