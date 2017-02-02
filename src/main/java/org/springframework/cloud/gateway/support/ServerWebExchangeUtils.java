package org.springframework.cloud.gateway.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class ServerWebExchangeUtils {
	private static final Log logger = LogFactory.getLog(ServerWebExchangeUtils.class);

	public static final String CLIENT_RESPONSE_ATTR = "webHandlerClientResponse";
	public static final String GATEWAY_ROUTE_ATTR = "gatewayRoute";
	public static final String GATEWAY_REQUEST_URL_ATTR = "gatewayRequestUrl";
	public static final String GATEWAY_HANDLER_MAPPER_ATTR = "gatewayHandlerMapper";

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

	public static boolean setResponseStatus(ServerWebExchange exchange, HttpStatus httpStatus) {
		boolean response = exchange.getResponse().setStatusCode(httpStatus);
		if (!response && logger.isWarnEnabled()) {
			logger.warn("Unable to set status code to "+ httpStatus + ". Response already committed.");
		}
		return response;
	}

	public static HttpStatus parse(String statusString) {
		HttpStatus httpStatus;

		try {
			int status = Integer.parseInt(statusString);
			httpStatus = HttpStatus.valueOf(status);
		} catch (NumberFormatException e) {
			// try the enum string
			httpStatus = HttpStatus.valueOf(statusString.toUpperCase());
		}
		return httpStatus;
	}
}
