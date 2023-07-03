/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.common;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.web.servlet.function.RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

// TODO: maybe rename to ServerRequestUtils?
public abstract class MvcUtils {

	/**
	 * Gateway request URL attribute name.
	 */
	public static final String GATEWAY_REQUEST_URL_ATTR = qualify("gatewayRequestUrl");

	/**
	 * Gateway route ID attribute name.
	 */
	public static final String GATEWAY_ROUTE_ID_ATTR = qualify("gatewayRouteId");

	private MvcUtils() {
	}

	private static String qualify(String attr) {
		return "GatewayServerMvc." + attr;
	}

	public static String expand(ServerRequest request, String template) {
		Assert.notNull(request, "request may not be null");
		Assert.notNull(template, "template may not be null");

		if (template.indexOf('{') == -1) { // short circuit
			return template;
		}
		Map<String, Object> variables = getUriTemplateVariables(request);
		return UriComponentsBuilder.fromPath(template).build().expand(variables).getPath();
	}

	public static String[] expandMultiple(ServerRequest request, String... templates) {
		List<String> expanded = Arrays.stream(templates).map(value -> MvcUtils.expand(request, value)).toList();
		return expanded.toArray(new String[0]);
	}

	public static ApplicationContext getApplicationContext(ServerRequest request) {
		WebApplicationContext webApplicationContext = RequestContextUtils
				.findWebApplicationContext(request.servletRequest());
		if (webApplicationContext == null) {
			throw new IllegalStateException("No Application Context in request attributes");
		}
		return webApplicationContext;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> getUriTemplateVariables(ServerRequest request) {
		return (Map<String, Object>) request.attributes().getOrDefault(URI_TEMPLATE_VARIABLES_ATTRIBUTE,
				new HashMap<>());
	}

	@SuppressWarnings("unchecked")
	public static void putUriTemplateVariables(ServerRequest request, Map<String, String> uriVariables) {
		if (request.attributes().containsKey(URI_TEMPLATE_VARIABLES_ATTRIBUTE)) {
			Map<String, Object> existingVariables = (Map<String, Object>) request.attributes()
					.get(URI_TEMPLATE_VARIABLES_ATTRIBUTE);
			HashMap<String, Object> newVariables = new HashMap<>(existingVariables);
			newVariables.putAll(uriVariables);
			request.attributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, newVariables);
		}
		else {
			request.attributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables);
		}
	}

	public static void setRouteId(ServerRequest request, String routeId) {
		request.attributes().put(GATEWAY_ROUTE_ID_ATTR, routeId);
		request.servletRequest().setAttribute(GATEWAY_ROUTE_ID_ATTR, routeId);
	}

	public static void setRequestUrl(ServerRequest request, URI url) {
		request.attributes().put(GATEWAY_REQUEST_URL_ATTR, url);
		request.servletRequest().setAttribute(GATEWAY_REQUEST_URL_ATTR, url);
	}

}
