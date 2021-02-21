/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.removeAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.reset;

/**
 * @author Ryan Baxter
 */
public abstract class SpringCloudCircuitBreakerFilterFactory
		extends AbstractGatewayFilterFactory<SpringCloudCircuitBreakerFilterFactory.Config> {

	/** CircuitBreaker component name. */
	public static final String NAME = "CircuitBreaker";

	private ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

	private ReactiveCircuitBreaker cb;

	private final ObjectProvider<DispatcherHandler> dispatcherHandlerProvider;

	// do not use this dispatcherHandler directly, use getDispatcherHandler() instead.
	private volatile DispatcherHandler dispatcherHandler;

	public SpringCloudCircuitBreakerFilterFactory(ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		super(Config.class);
		this.reactiveCircuitBreakerFactory = reactiveCircuitBreakerFactory;
		this.dispatcherHandlerProvider = dispatcherHandlerProvider;
	}

	private DispatcherHandler getDispatcherHandler() {
		if (dispatcherHandler == null) {
			dispatcherHandler = dispatcherHandlerProvider.getIfAvailable();
		}

		return dispatcherHandler;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return singletonList(NAME_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		ReactiveCircuitBreaker cb = reactiveCircuitBreakerFactory.create(config.getId());
		Set<HttpStatus> statuses = config.getStatusCodes().stream().map(HttpStatusHolder::parse)
				.filter(statusHolder -> statusHolder.getHttpStatus() != null).map(HttpStatusHolder::getHttpStatus)
				.collect(Collectors.toSet());

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return cb.run(chain.filter(exchange).doOnSuccess(v -> {
					if (statuses.contains(exchange.getResponse().getStatusCode())) {
						HttpStatus status = exchange.getResponse().getStatusCode();
						exchange.getResponse().setStatusCode(null);
						reset(exchange);
						throw new CircuitBreakerStatusCodeException(status);
					}
				}), t -> {
					if (config.getFallbackUri() == null) {
						return Mono.error(t);
					}

					// TODO: copied from RouteToRequestUrlFilter
					URI uri = exchange.getRequest().getURI();
					// TODO: assume always?
					boolean encoded = containsEncodedParts(uri);
					URI requestUrl = UriComponentsBuilder.fromUri(uri).host(null).port(null)
							.uri(config.getFallbackUri()).scheme(null).build(encoded).toUri();
					exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
					addExceptionDetails(t, exchange);

					// Before we continue on remove the already routed attribute since the
					// fallback may go back through the route handler if the fallback
					// is to another route in the Gateway
					removeAlreadyRouted(exchange);

					ServerHttpRequest request = exchange.getRequest().mutate().uri(requestUrl).build();
					return getDispatcherHandler().handle(exchange.mutate().request(request).build());
				}).onErrorResume(t -> handleErrorWithoutFallback(t));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SpringCloudCircuitBreakerFilterFactory.this)
						.append("name", config.getName()).append("fallback", config.fallbackUri).toString();
			}
		};
	}

	protected abstract Mono<Void> handleErrorWithoutFallback(Throwable t);

	private void addExceptionDetails(Throwable t, ServerWebExchange exchange) {
		ofNullable(t).ifPresent(
				exception -> exchange.getAttributes().put(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception));
	}

	@Override
	public String name() {
		return NAME;
	}

	public static class Config implements HasRouteId {

		private String name;

		private URI fallbackUri;

		private String routeId;

		private Set<String> statusCodes = new HashSet<>();

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		public String getRouteId() {
			return routeId;
		}

		public URI getFallbackUri() {
			return fallbackUri;
		}

		public Config setFallbackUri(URI fallbackUri) {
			this.fallbackUri = fallbackUri;
			return this;
		}

		public Config setFallbackUri(String fallbackUri) {
			return setFallbackUri(URI.create(fallbackUri));
		}

		public String getName() {
			return name;
		}

		public Config setName(String name) {
			this.name = name;
			return this;
		}

		public String getId() {
			if (StringUtils.isEmpty(name) && !StringUtils.isEmpty(routeId)) {
				return routeId;
			}
			return name;
		}

		public Set<String> getStatusCodes() {
			return statusCodes;
		}

		public Config setStatusCodes(Set<String> statusCodes) {
			this.statusCodes = statusCodes;
			return this;
		}

		public Config addStatusCode(String statusCode) {
			this.statusCodes.add(statusCode);
			return this;
		}

	}

	public class CircuitBreakerStatusCodeException extends HttpStatusCodeException {

		public CircuitBreakerStatusCodeException(HttpStatus statusCode) {
			super(statusCode);
		}

	}

}
