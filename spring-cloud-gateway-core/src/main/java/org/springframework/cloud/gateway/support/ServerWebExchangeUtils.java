/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.support;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Spencer Gibb
 */
public final class ServerWebExchangeUtils {

	/**
	 * Preserve-Host header attribute name.
	 */
	public static final String PRESERVE_HOST_HEADER_ATTRIBUTE = qualify(
			"preserveHostHeader");

	/**
	 * URI template variables attribute name.
	 */
	public static final String URI_TEMPLATE_VARIABLES_ATTRIBUTE = qualify(
			"uriTemplateVariables");

	/**
	 * Client response attribute name.
	 */
	public static final String CLIENT_RESPONSE_ATTR = qualify("gatewayClientResponse");

	/**
	 * Client response connection attribute name.
	 */
	public static final String CLIENT_RESPONSE_CONN_ATTR = qualify(
			"gatewayClientResponseConnection");

	/**
	 * Client response header names attribute name.
	 */
	public static final String CLIENT_RESPONSE_HEADER_NAMES = qualify(
			"gatewayClientResponseHeaderNames");

	/**
	 * Gateway route attribute name.
	 */
	public static final String GATEWAY_ROUTE_ATTR = qualify("gatewayRoute");

	/**
	 * Gateway request URL attribute name.
	 */
	public static final String GATEWAY_REQUEST_URL_ATTR = qualify("gatewayRequestUrl");

	/**
	 * Gateway original request URL attribute name.
	 */
	public static final String GATEWAY_ORIGINAL_REQUEST_URL_ATTR = qualify(
			"gatewayOriginalRequestUrl");

	/**
	 * Gateway handler mapper attribute name.
	 */
	public static final String GATEWAY_HANDLER_MAPPER_ATTR = qualify(
			"gatewayHandlerMapper");

	/**
	 * Gateway scheme prefix attribute name.
	 */
	public static final String GATEWAY_SCHEME_PREFIX_ATTR = qualify(
			"gatewaySchemePrefix");

	/**
	 * Gateway predicate route attribute name.
	 */
	public static final String GATEWAY_PREDICATE_ROUTE_ATTR = qualify(
			"gatewayPredicateRouteAttr");

	/**
	 * Weight attribute name.
	 */
	public static final String WEIGHT_ATTR = qualify("routeWeight");

	/**
	 * Original response Content-Type attribute name.
	 */
	public static final String ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR = "original_response_content_type";

	/**
	 * Hystrix execution exception attribute name.
	 */
	public static final String HYSTRIX_EXECUTION_EXCEPTION_ATTR = qualify(
			"hystrixExecutionException");

	/**
	 * Used when a routing filter has been successfully called. Allows users to write
	 * custom routing filters that disable built in routing filters.
	 */
	public static final String GATEWAY_ALREADY_ROUTED_ATTR = qualify(
			"gatewayAlreadyRouted");

	/**
	 * Gateway already prefixed attribute name.
	 */
	public static final String GATEWAY_ALREADY_PREFIXED_ATTR = qualify(
			"gatewayAlreadyPrefixed");

	private static final Log logger = LogFactory.getLog(ServerWebExchangeUtils.class);

	private ServerWebExchangeUtils() {
		throw new AssertionError("Must not instantiate utility class.");
	}

	private static String qualify(String attr) {
		return ServerWebExchangeUtils.class.getName() + "." + attr;
	}

	public static void setAlreadyRouted(ServerWebExchange exchange) {
		exchange.getAttributes().put(GATEWAY_ALREADY_ROUTED_ATTR, true);
	}

	public static boolean isAlreadyRouted(ServerWebExchange exchange) {
		return exchange.getAttributeOrDefault(GATEWAY_ALREADY_ROUTED_ATTR, false);
	}

	public static boolean setResponseStatus(ServerWebExchange exchange,
			HttpStatus httpStatus) {
		boolean response = exchange.getResponse().setStatusCode(httpStatus);
		if (!response && logger.isWarnEnabled()) {
			logger.warn("Unable to set status code to " + httpStatus
					+ ". Response already committed.");
		}
		return response;
	}

	public static boolean setResponseStatus(ServerWebExchange exchange,
			HttpStatusHolder statusHolder) {
		if (exchange.getResponse().isCommitted()) {
			return false;
		}
		if (statusHolder.getHttpStatus() != null) {
			return setResponseStatus(exchange, statusHolder.getHttpStatus());
		}
		if (statusHolder.getStatus() != null
				&& exchange.getResponse() instanceof AbstractServerHttpResponse) { // non-standard
			((AbstractServerHttpResponse) exchange.getResponse())
					.setStatusCodeValue(statusHolder.getStatus());
			return true;
		}
		return false;
	}

	public static boolean containsEncodedParts(URI uri) {
		boolean encoded = (uri.getRawQuery() != null && uri.getRawQuery().contains("%"))
				|| (uri.getRawPath() != null && uri.getRawPath().contains("%"));

		// Verify if it is really fully encoded. Treat partial encoded as uncoded.
		if (encoded) {
			try {
				UriComponentsBuilder.fromUri(uri).build(true);
				return true;
			}
			catch (IllegalArgumentException ignore) {
			}

			return false;
		}

		return encoded;
	}

	public static HttpStatus parse(String statusString) {
		HttpStatus httpStatus;

		try {
			int status = Integer.parseInt(statusString);
			httpStatus = HttpStatus.resolve(status);
		}
		catch (NumberFormatException e) {
			// try the enum string
			httpStatus = HttpStatus.valueOf(statusString.toUpperCase());
		}
		return httpStatus;
	}

	public static void addOriginalRequestUrl(ServerWebExchange exchange, URI url) {
		exchange.getAttributes().computeIfAbsent(GATEWAY_ORIGINAL_REQUEST_URL_ATTR,
				s -> new LinkedHashSet<>());
		LinkedHashSet<URI> uris = exchange
				.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		uris.add(url);
	}

	public static AsyncPredicate<ServerWebExchange> toAsyncPredicate(
			Predicate<? super ServerWebExchange> predicate) {
		Assert.notNull(predicate, "predicate must not be null");
		return t -> Mono.just(predicate.test(t));
	}

	@SuppressWarnings("unchecked")
	public static void putUriTemplateVariables(ServerWebExchange exchange,
			Map<String, String> uriVariables) {
		if (exchange.getAttributes().containsKey(URI_TEMPLATE_VARIABLES_ATTRIBUTE)) {
			Map<String, Object> existingVariables = (Map<String, Object>) exchange
					.getAttributes().get(URI_TEMPLATE_VARIABLES_ATTRIBUTE);
			HashMap<String, Object> newVariables = new HashMap<>();
			newVariables.putAll(existingVariables);
			newVariables.putAll(uriVariables);
			exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, newVariables);
		}
		else {
			exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables);
		}
	}

	public static Map<String, String> getUriTemplateVariables(
			ServerWebExchange exchange) {
		return exchange.getAttributeOrDefault(URI_TEMPLATE_VARIABLES_ATTRIBUTE,
				new HashMap<>());
	}

}
