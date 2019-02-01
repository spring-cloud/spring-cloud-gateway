/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.rsocket.server;

import org.springframework.cloud.gateway.rsocket.filter.AbstractFilterChain;

import java.util.List;

public class GatewayFilterChain
		extends AbstractFilterChain<GatewayFilter, GatewayExchange, GatewayFilterChain> {

	/**
	 * Public constructor with the list of filters and the target handler to use.
	 *
	 * @param filters the filters ahead of the handler
	 */
	public GatewayFilterChain(List<GatewayFilter> filters) {
		super(filters);
	}

	public GatewayFilterChain(List<GatewayFilter> allFilters, GatewayFilter currentFilter, GatewayFilterChain next) {
		super(allFilters, currentFilter, next);
	}

	@Override
	protected GatewayFilterChain create(List<GatewayFilter> allFilters, GatewayFilter currentFilter, GatewayFilterChain next) {
		return new GatewayFilterChain(allFilters, currentFilter, next);
	}
}
