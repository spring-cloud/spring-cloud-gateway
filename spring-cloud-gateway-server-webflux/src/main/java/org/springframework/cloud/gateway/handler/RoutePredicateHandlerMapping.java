/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.DIFFERENT;
import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.DISABLED;
import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.SAME;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REACTOR_CONTEXT_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Spencer Gibb
 * @author Fredrich Ombico
 * @author Abel Salgado Romero
 * @author Yavor Chamov
 * @author Nan Chiu
 */
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {

	private static final String CORS_METADATA_KEY = "cors";

	private final FilteringWebHandler webHandler;

	private final RouteLocator routeLocator;

	private final @Nullable Integer managementPort;

	private final ManagementPortType managementPortType;

	public RoutePredicateHandlerMapping(FilteringWebHandler webHandler, RouteLocator routeLocator,
			GlobalCorsProperties globalCorsProperties, Environment environment) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		this.managementPort = getPortProperty(environment, "management.server.");
		this.managementPortType = getManagementPortType(environment);
		setOrder(environment.getProperty(GatewayProperties.PREFIX + ".handler-mapping.order", Integer.class, 1));
		setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

	private ManagementPortType getManagementPortType(Environment environment) {
		Integer serverPort = getPortProperty(environment, "server.");
		if (this.managementPort != null && this.managementPort < 0) {
			return DISABLED;
		}
		return ((this.managementPort == null || (serverPort == null && this.managementPort.equals(8080))
				|| (this.managementPort != 0 && this.managementPort.equals(serverPort))) ? SAME : DIFFERENT);
	}

	private static @Nullable Integer getPortProperty(Environment environment, String prefix) {
		return environment.getProperty(prefix + "port", Integer.class);
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		// don't handle requests on management port if set and different than server port
		if (this.managementPortType == DIFFERENT && this.managementPort != null
				&& exchange.getRequest().getLocalAddress() != null
				&& exchange.getRequest().getLocalAddress().getPort() == this.managementPort) {
			return Mono.empty();
		}
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getSimpleName());

		return Mono.deferContextual(contextView -> {
			exchange.getAttributes().put(GATEWAY_REACTOR_CONTEXT_ATTR, contextView);
			return lookupRoute(exchange)
				// .log("route-predicate-handler-mapping", Level.FINER) //name this
				.map((Function<Route, ?>) r -> {
					exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
					if (logger.isDebugEnabled()) {
						logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + r);
					}

					exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, r);
					return webHandler;
				})
				.switchIfEmpty(Mono.empty().then(Mono.fromRunnable(() -> {
					exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
					ServerWebExchangeUtils.clearCachedRequestBody(exchange);
					if (logger.isTraceEnabled()) {
						logger.trace("No RouteDefinition found for [" + getExchangeDesc(exchange) + "]");
					}
				})));
		});
	}

	/**
	 * Returns CORS configuration for the current request.
	 *
	 * <p>
	 * Retrieves route-level CORS config from the matched route's metadata. If present,
	 * returns it directly (route-specific CORS takes precedence). Otherwise, falls back
	 * to global CORS configurations.
	 * </p>
	 *
	 * <p>
	 * Route-level CORS is defined in route metadata under key {@code "cors"} with
	 * properties: allowedOrigins, allowedOriginPatterns, allowedMethods, allowedHeaders,
	 * exposedHeaders, allowCredentials, maxAge.
	 * </p>
	 * @param handler the handler to check (never {@code null})
	 * @param exchange the current exchange
	 * @return the CORS configuration for the handler, or {@code null} if none
	 */
	@Override
	protected @Nullable CorsConfiguration getCorsConfiguration(Object handler, ServerWebExchange exchange) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		if (route == null) {
			return super.getCorsConfiguration(handler, exchange);
		}
		// Route-level CORS config is associated with the matched route, return directly
		// if present
		Optional<CorsConfiguration> corsConfiguration = getCorsConfiguration(route);

		return corsConfiguration.orElseGet(() -> super.getCorsConfiguration(handler, exchange));
	}

	@SuppressWarnings("unchecked")
	private Optional<CorsConfiguration> getCorsConfiguration(Route route) {
		Map<String, Object> corsMetadata = (Map<String, Object>) route.getMetadata().get(CORS_METADATA_KEY);
		if (corsMetadata != null) {
			final CorsConfiguration corsConfiguration = new CorsConfiguration();

			findValue(corsMetadata, "allowCredentials")
				.ifPresent(value -> corsConfiguration.setAllowCredentials((Boolean) value));
			findValue(corsMetadata, "allowedHeaders")
				.ifPresent(value -> corsConfiguration.setAllowedHeaders(asList(value)));
			findValue(corsMetadata, "allowedMethods")
				.ifPresent(value -> corsConfiguration.setAllowedMethods(asList(value)));
			findValue(corsMetadata, "allowedOriginPatterns")
				.ifPresent(value -> corsConfiguration.setAllowedOriginPatterns(asList(value)));
			findValue(corsMetadata, "allowedOrigins")
				.ifPresent(value -> corsConfiguration.setAllowedOrigins(asList(value)));
			findValue(corsMetadata, "exposedHeaders")
				.ifPresent(value -> corsConfiguration.setExposedHeaders(asList(value)));
			findValue(corsMetadata, "maxAge").ifPresent(value -> corsConfiguration.setMaxAge(asLong(value)));

			return Optional.of(corsConfiguration);
		}

		return Optional.empty();
	}

	private Optional<Object> findValue(Map<String, Object> metadata, String key) {
		Object value = metadata.get(key);
		return Optional.ofNullable(value);
	}

	private List<String> asList(Object value) {
		if (value instanceof String) {
			return Arrays.asList((String) value);
		}
		if (value instanceof Map) {
			return new ArrayList<>(((Map<?, String>) value).values());
		}
		else {
			return (List<String>) value;
		}
	}

	private Long asLong(Object value) {
		if (value instanceof Integer) {
			return ((Integer) value).longValue();
		}
		else {
			return (Long) value;
		}
	}

	// TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}

	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
		return this.routeLocator.getRoutes().filterWhen(route -> {
			// add the current route we are testing
			exchange.getAttributes().put(GATEWAY_PREDICATE_ROUTE_ATTR, route.getId());
			try {
				return route.getPredicate().apply(exchange);
			}
			catch (Exception e) {
				logger.error("Error applying predicate for route: " + route.getId(), e);
			}
			return Mono.just(false);
		})
			.next()
			// TODO: error handling
			.map(route -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Route matched: " + route.getId());
				}
				validateRoute(route, exchange);
				return route;
			});

		/*
		 * TODO: trace logging if (logger.isTraceEnabled()) {
		 * logger.trace("RouteDefinition did not match: " + routeDefinition.getId()); }
		 */
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>
	 * The default implementation is empty. Can be overridden in subclasses, for example
	 * to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) {
	}

	protected String getSimpleName() {
		return "RoutePredicateHandlerMapping";
	}

	public enum ManagementPortType {

		/**
		 * The management port has been disabled.
		 */
		DISABLED,

		/**
		 * The management port is the same as the server port.
		 */
		SAME,

		/**
		 * The management port and server port are different.
		 */
		DIFFERENT;

	}

}
