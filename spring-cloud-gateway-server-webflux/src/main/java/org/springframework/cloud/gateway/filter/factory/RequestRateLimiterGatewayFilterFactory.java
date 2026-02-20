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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * User Request Rate Limiter filter. See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34.
 */
@ConfigurationProperties("spring.cloud.gateway.server.webflux.filter.request-rate-limiter")
public class RequestRateLimiterGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestRateLimiterGatewayFilterFactory.Config> {

	/**
	 * Key-Resolver key.
	 */
	public static final String KEY_RESOLVER_KEY = "keyResolver";

	private static final String EMPTY_KEY = "____EMPTY_KEY__";

	private final RateLimiter defaultRateLimiter;

	private final KeyResolver defaultKeyResolver;

	/**
	 * Switch to deny requests if the Key Resolver returns an empty key, defaults to true.
	 */
	private boolean denyEmptyKey = true;

	/** HttpStatus to return when denyEmptyKey is true, defaults to FORBIDDEN. */
	private String emptyKeyStatusCode = HttpStatus.FORBIDDEN.name();

	/**
	 * Switch to throw a {@link HttpClientErrorException} when the request is denied by
	 * the RateLimiter, defaults to false.
	 */
	private boolean throwOnLimit = false;

	public RequestRateLimiterGatewayFilterFactory(RateLimiter defaultRateLimiter, KeyResolver defaultKeyResolver) {
		super(Config.class);
		this.defaultRateLimiter = defaultRateLimiter;
		this.defaultKeyResolver = defaultKeyResolver;
	}

	public KeyResolver getDefaultKeyResolver() {
		return defaultKeyResolver;
	}

	public RateLimiter getDefaultRateLimiter() {
		return defaultRateLimiter;
	}

	public boolean isDenyEmptyKey() {
		return denyEmptyKey;
	}

	public void setDenyEmptyKey(boolean denyEmptyKey) {
		this.denyEmptyKey = denyEmptyKey;
	}

	public String getEmptyKeyStatusCode() {
		return emptyKeyStatusCode;
	}

	public void setEmptyKeyStatusCode(String emptyKeyStatusCode) {
		this.emptyKeyStatusCode = emptyKeyStatusCode;
	}

	public boolean isThrowOnLimit() {
		return throwOnLimit;
	}

	public void setThrowOnLimit(boolean throwOnLimit) {
		this.throwOnLimit = throwOnLimit;
	}

	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Config config) {
		KeyResolver resolver = getOrDefault(config.keyResolver, defaultKeyResolver);
		RateLimiter<Object> limiter = getOrDefault(config.rateLimiter, defaultRateLimiter);
		boolean denyEmpty = getOrDefault(config.denyEmptyKey, this.denyEmptyKey);
		HttpStatusHolder emptyKeyStatus = HttpStatusHolder
			.parse(getOrDefault(config.emptyKeyStatus, this.emptyKeyStatusCode));
		boolean throwLimit = getOrDefault(config.throwOnLimit, this.throwOnLimit);

		return (exchange, chain) -> resolver.resolve(exchange).defaultIfEmpty(EMPTY_KEY).flatMap(key -> {
			if (EMPTY_KEY.equals(key)) {
				if (denyEmpty) {
					setResponseStatus(exchange, emptyKeyStatus);
					return exchange.getResponse().setComplete();
				}
				return chain.filter(exchange);
			}
			String routeId = config.getRouteId();
			if (routeId == null) {
				Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
				routeId = Objects.requireNonNull(route, "Route not found").getId();
			}
			return limiter.isAllowed(routeId, key).flatMap(response -> {

				for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
					exchange.getResponse().getHeaders().add(header.getKey(), header.getValue());
				}

				if (response.isAllowed()) {
					return chain.filter(exchange);
				}

				if (throwLimit) {
					return Mono.error(HttpClientErrorException.create(config.getStatusCode(), "Too Many Requests",
							exchange.getResponse().getHeaders(), null, null));
				}

				setResponseStatus(exchange, config.getStatusCode());
				return exchange.getResponse().setComplete();
			});
		});
	}

	private <T> T getOrDefault(@Nullable T configValue, T defaultValue) {
		return (configValue != null) ? configValue : defaultValue;
	}

	public static class Config implements HasRouteId {

		private @Nullable KeyResolver keyResolver;

		private @Nullable RateLimiter rateLimiter;

		private HttpStatus statusCode = HttpStatus.TOO_MANY_REQUESTS;

		private @Nullable Boolean denyEmptyKey;

		private @Nullable String emptyKeyStatus;

		private @Nullable Boolean throwOnLimit;

		private @Nullable String routeId;

		public @Nullable KeyResolver getKeyResolver() {
			return keyResolver;
		}

		public Config setKeyResolver(KeyResolver keyResolver) {
			this.keyResolver = keyResolver;
			return this;
		}

		public @Nullable RateLimiter getRateLimiter() {
			return rateLimiter;
		}

		public Config setRateLimiter(RateLimiter rateLimiter) {
			this.rateLimiter = rateLimiter;
			return this;
		}

		public HttpStatus getStatusCode() {
			return statusCode;
		}

		public Config setStatusCode(HttpStatus statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		public @Nullable Boolean getDenyEmptyKey() {
			return denyEmptyKey;
		}

		public Config setDenyEmptyKey(Boolean denyEmptyKey) {
			this.denyEmptyKey = denyEmptyKey;
			return this;
		}

		public @Nullable String getEmptyKeyStatus() {
			return emptyKeyStatus;
		}

		public Config setEmptyKeyStatus(String emptyKeyStatus) {
			this.emptyKeyStatus = emptyKeyStatus;
			return this;
		}

		public @Nullable Boolean getThrowOnLimit() {
			return throwOnLimit;
		}

		public Config setThrowOnLimit(Boolean throwOnLimit) {
			this.throwOnLimit = throwOnLimit;
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public @Nullable String getRouteId() {
			return this.routeId;
		}

	}

}
