package org.springframework.cloud.gateway.support;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class ServerWebExchangeUtils {

	public static final String CLIENT_RESPONSE_ATTR = "webHandlerClientResponse";
	public static final String GATEWAY_ROUTE_ATTR = "gatewayRoute";
	public static final String GATEWAY_REQUEST_URL_ATTR = "gatewayRequestUrl";
	public static final String GATEWAY_HANDLER_MAPPER_ATTR = "gatewayHandlerMapper";
	public static final String RESPONSE_COMMITTED_ATTR = "responseCommitted";

	public static <T> T getAttribute(ServerWebExchange exchange, String attributeName, Class<T> type) {
		if (exchange.getAttributes().containsKey(attributeName)) {
			Object attr = exchange.getAttributes().get(attributeName);
			if (type.isAssignableFrom(attr.getClass())) {
				return type.cast(attr);
			}
			throw new ClassCastException(attributeName + " is not of type " + type);
		}
		return null;
	}

	public static boolean isResponseCommitted(ServerWebExchange exchange) {
		Boolean responseCommitted = getAttribute(exchange, RESPONSE_COMMITTED_ATTR, Boolean.class);
		return responseCommitted != null && responseCommitted;
	}
}
