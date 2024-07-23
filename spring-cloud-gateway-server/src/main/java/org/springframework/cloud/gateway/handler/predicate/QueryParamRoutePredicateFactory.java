/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * A predicate that checks if a query parameter value matches criteria of a given
 * predicate.
 *
 * @author Francesco Poli
 */
public class QueryParamRoutePredicateFactory
		extends AbstractRoutePredicateFactory<QueryParamRoutePredicateFactory.Config> {

	public QueryParamRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {

			@Override
			public boolean test(ServerWebExchange exchange) {
				List<String> values = exchange.getRequest().getQueryParams().get(config.param);
				if (values == null) {
					return false;
				}
				for (String value : values) {
					if (value != null && config.predicate.test(value)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("QueryParam: param=%s", config.param);
			}
		};
	}

	/**
	 * {@link QueryParamRoutePredicateFactory} configuration class.
	 *
	 * @author Francesco Poli
	 */
	@Validated
	public static class Config {

		@NotEmpty
		private String param;

		@NotNull
		private Predicate<String> predicate;

		public Config setParam(String param) {
			this.param = param;
			return this;
		}

		public Config setPredicate(Predicate<String> predicate) {
			this.predicate = predicate;
			return this;
		}

	}

}
