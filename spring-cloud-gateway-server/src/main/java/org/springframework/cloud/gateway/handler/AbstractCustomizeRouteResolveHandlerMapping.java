package org.springframework.cloud.gateway.handler;

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
public abstract class AbstractCustomizeRouteResolveHandlerMapping extends AbstractHandlerMapping {

	public static final String CUSTOMIZE_ROUTE_DEFINITION_ID_KEY = "CUSTOMIZE_ROUTE_DEFINITION_ID_KEY";

	public AbstractCustomizeRouteResolveHandlerMapping() {
		/**
		 * We need to ensure that it is executed before {@link RoutePredicateHandlerMapping}
		 */
		setOrder(0);
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange serverWebExchange) {
		String customizeRouteDefinitionId = resolveRouteId(serverWebExchange);
		if (customizeRouteDefinitionId != null) {
			serverWebExchange.getAttributes().put(CUSTOMIZE_ROUTE_DEFINITION_ID_KEY, customizeRouteDefinitionId);
		}

		return Mono.empty();
	}

	/**
	 * The user-defined routing information is parsed through {@link ServerWebExchange}
	 * <p>
	 * You can use this method again to resolve a routing ID through server webexchange according to your own routing rules
	 *
	 * @param serverWebExchange
	 * @return Route definition ID resolved according to custom routing rules
	 */
	protected String resolveRouteId(ServerWebExchange serverWebExchange) {
		return null;
	}
}
