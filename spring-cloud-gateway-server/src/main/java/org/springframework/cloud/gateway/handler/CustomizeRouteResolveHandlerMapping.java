package org.springframework.cloud.gateway.handler;

import org.springframework.cloud.gateway.route.CustomizeRouteIdResolve;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Custom route resolution processor
 * <p>
 * In some scenarios, using a user-defined routing resolution strategy will be faster than a general regular resolution strategy,
 * so SCG will provide this ability to customize routing resolution
 *
 * @author: yizhenqiang
 * @date: 2021/4/28 9:24 下午
 */
public class CustomizeRouteResolveHandlerMapping extends AbstractHandlerMapping {

	public static final String CUSTOMIZE_ROUTE_DEFINITION_ID_KEY = "CUSTOMIZE_ROUTE_DEFINITION_ID_KEY";

	private CustomizeRouteIdResolve customizeRouteIdResolve;

	public CustomizeRouteResolveHandlerMapping(CustomizeRouteIdResolve customizeRouteIdResolve) {
		this();
		this.customizeRouteIdResolve = customizeRouteIdResolve;
	}

	public CustomizeRouteResolveHandlerMapping() {
		/**
		 * We need to ensure that it is executed before {@link RoutePredicateHandlerMapping}
		 */
		setOrder(0);
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange serverWebExchange) {
		if (customizeRouteIdResolve != null) {
			String customizeRouteDefinitionId = customizeRouteIdResolve.resolveRouteId(serverWebExchange);
			if (customizeRouteDefinitionId != null) {
				serverWebExchange.getAttributes().put(CUSTOMIZE_ROUTE_DEFINITION_ID_KEY, customizeRouteDefinitionId);
			}
		}

		return Mono.empty();
	}

	public void setCustomizeRouteIdResolve(CustomizeRouteIdResolve customizeRouteIdResolve) {
		this.customizeRouteIdResolve = customizeRouteIdResolve;
	}
}
