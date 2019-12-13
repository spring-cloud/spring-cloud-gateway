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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class MethodRoutePredicateFactory
		extends AbstractRoutePredicateFactory<MethodRoutePredicateFactory.Config> {

	/**
	 * Method key.
	 */
	public static final String METHOD_KEY = "method";

	public MethodRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(METHOD_KEY);
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				String requestMethod = exchange.getRequest().getMethod().name();
				return config.getMethod().stream().anyMatch(
						httpMethod -> requestMethod.equalsIgnoreCase(httpMethod));
			}

			@Override
			public String toString() {
				return String.format("Methods: %s", config.getMethod());
			}
		};
	}

	@Validated
	public static class Config {

		private List<String> method;

		public List<String> getMethod() {
			return method;
		}

		public void setMethod(List<String> methods) {
			this.method = methods;
		}

	}

}
