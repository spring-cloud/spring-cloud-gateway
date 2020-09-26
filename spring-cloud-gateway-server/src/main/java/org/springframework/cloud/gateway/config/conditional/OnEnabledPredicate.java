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

package org.springframework.cloud.gateway.config.conditional;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.web.server.ServerWebExchange;

public class OnEnabledPredicate extends OnEnabledComponent<RoutePredicateFactory<?>> {

	@Override
	protected String normalizeComponentName(Class<? extends RoutePredicateFactory<?>> predicateClass) {
		return "predicate." + NameUtils.normalizeRoutePredicateNameAsProperty(predicateClass);
	}

	@Override
	protected Class<?> annotationClass() {
		return ConditionalOnEnabledPredicate.class;
	}

	@Override
	protected Class<? extends RoutePredicateFactory<?>> defaultValueClass() {
		return DefaultValue.class;
	}

	static class DefaultValue implements RoutePredicateFactory<Object> {

		@Override
		public Predicate<ServerWebExchange> apply(Consumer<Object> consumer) {
			throw new UnsupportedOperationException("class DefaultValue is never meant to be intantiated");
		}

		@Override
		public Predicate<ServerWebExchange> apply(Object config) {
			throw new UnsupportedOperationException("class DefaultValue is never meant to be intantiated");
		}

	}

}
