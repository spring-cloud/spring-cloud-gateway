/*
 * Copyright 2013-2017 the original author or authors.
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

	public static final String URI_TEMPLATE_VARIABLES_ATTRIBUTE =
			ServerWebExchangeUtils.class.getName() + ".uriTemplateVariables";

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
