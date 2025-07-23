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

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.web.server.ServerWebExchange;

public class OnEnabledGlobalFilter extends OnEnabledComponent<GlobalFilter> {

	@Override
	protected String normalizeComponentName(Class<? extends GlobalFilter> filterClass) {
		return "global-filter." + NameUtils.normalizeGlobalFilterNameAsProperty(filterClass);
	}

	@Override
	protected Class<?> annotationClass() {
		return ConditionalOnEnabledGlobalFilter.class;
	}

	@Override
	protected Class<? extends GlobalFilter> defaultValueClass() {
		return DefaultValue.class;
	}

	static class DefaultValue implements GlobalFilter {

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			throw new UnsupportedOperationException("class DefaultValue is never meant to be intantiated");
		}

	}

}
