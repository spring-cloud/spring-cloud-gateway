/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.socketacceptor;

import java.util.List;

import org.springframework.cloud.gateway.rsocket.filter.AbstractFilterChain;

public class SocketAcceptorFilterChain extends
		AbstractFilterChain<SocketAcceptorFilter, SocketAcceptorExchange, SocketAcceptorFilterChain> {

	/**
	 * Public constructor with the list of filters and the target handler to use.
	 * @param filters the filters ahead of the handler
	 */
	public SocketAcceptorFilterChain(List<SocketAcceptorFilter> filters) {
		super(filters);
	}

	public SocketAcceptorFilterChain(List<SocketAcceptorFilter> allFilters,
			SocketAcceptorFilter currentFilter, SocketAcceptorFilterChain next) {
		super(allFilters, currentFilter, next);
	}

	@Override
	protected SocketAcceptorFilterChain create(List<SocketAcceptorFilter> allFilters,
			SocketAcceptorFilter currentFilter, SocketAcceptorFilterChain next) {
		return new SocketAcceptorFilterChain(allFilters, currentFilter, next);
	}

}
