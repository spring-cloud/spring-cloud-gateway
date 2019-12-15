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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.KeyResolver;
import org.springframework.cloud.gateway.filter.quota.QuotaFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * @author Tobias Schug
 */
@ConfigurationProperties("spring.cloud.gateway.filter.request-quota-filter")
public class RequestQuotaGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestQuotaGatewayFilterFactory.Config> {

	/**
	 * Key-Resolver key.
	 */
	public static final String KEY_RESOLVER_KEY = "keyResolver";

	private static final String EMPTY_KEY = "____EMPTY_KEY__";

	private final QuotaFilter defaultQuotaFilter;

	private final KeyResolver defaultKeyResolver;

	/**
	 * Switch to deny requests if the Key Resolver returns an empty key, defaults to true.
	 */
	private boolean denyEmptyKey = true;

	/** HttpStatus to return when denyEmptyKey is true, defaults to FORBIDDEN. */
	private String emptyKeyStatusCode = HttpStatus.FORBIDDEN.name();

	public RequestQuotaGatewayFilterFactory(QuotaFilter defaultQuotaFilter,
			KeyResolver defaultKeyResolver) {
		super(Config.class);
		this.defaultQuotaFilter = defaultQuotaFilter;
		this.defaultKeyResolver = defaultKeyResolver;
	}

	public KeyResolver getDefaultKeyResolver() {
		return defaultKeyResolver;
	}

	public QuotaFilter getDefaultQuotaFilter() {
		return defaultQuotaFilter;
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

	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Config config) {
		KeyResolver resolver = getOrDefault(config.keyResolver, defaultKeyResolver);
		QuotaFilter<Object> limiter = getOrDefault(config.quotaFilter,
				defaultQuotaFilter);
		boolean denyEmpty = getOrDefault(config.denyEmptyKey, this.denyEmptyKey);
		HttpStatusHolder emptyKeyStatus = HttpStatusHolder
				.parse(getOrDefault(config.emptyKeyStatus, this.emptyKeyStatusCode));

		return (exchange, chain) -> resolver.resolve(exchange).defaultIfEmpty(EMPTY_KEY)
				.flatMap(key -> {
					if (EMPTY_KEY.equals(key)) {
						if (denyEmpty) {
							setResponseStatus(exchange, emptyKeyStatus);
							return exchange.getResponse().setComplete();
						}
						return chain.filter(exchange);
					}
					String routeId = config.getRouteId();
					if (routeId == null) {
						Route route = exchange
								.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
						routeId = route.getId();
					}
					return limiter.isAllowed(routeId, key).flatMap(response -> {
						for (Map.Entry<String, String> header : response.getHeaders()
								.entrySet()) {
							exchange.getResponse().getHeaders().add(header.getKey(),
									header.getValue());
						}

						if (response.isAllowed()) {
							return chain.filter(exchange);
						}

						setResponseStatus(exchange, config.getStatusCode());
						return exchange.getResponse().setComplete();
					});
				});
	}

	private <T> T getOrDefault(T configValue, T defaultValue) {
		return (configValue != null) ? configValue : defaultValue;
	}

	public static class Config implements HasRouteId {

		private KeyResolver keyResolver;

		private QuotaFilter quotaFilter;

		private HttpStatus statusCode = HttpStatus.TOO_MANY_REQUESTS;

		private Boolean denyEmptyKey;

		private String emptyKeyStatus;

		private String routeId;

		public KeyResolver getKeyResolver() {
			return keyResolver;
		}

		public Config setKeyResolver(KeyResolver keyResolver) {
			this.keyResolver = keyResolver;
			return this;
		}

		public QuotaFilter getQuotaFilter() {
			return quotaFilter;
		}

		public Config setQuotaFilter(QuotaFilter quotaFilter) {
			this.quotaFilter = quotaFilter;
			return this;
		}

		public HttpStatus getStatusCode() {
			return statusCode;
		}

		public Config setStatusCode(HttpStatus statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		public Boolean getDenyEmptyKey() {
			return denyEmptyKey;
		}

		public Config setDenyEmptyKey(Boolean denyEmptyKey) {
			this.denyEmptyKey = denyEmptyKey;
			return this;
		}

		public String getEmptyKeyStatus() {
			return emptyKeyStatus;
		}

		public Config setEmptyKeyStatus(String emptyKeyStatus) {
			this.emptyKeyStatus = emptyKeyStatus;
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public String getRouteId() {
			return this.routeId;
		}

	}

}
