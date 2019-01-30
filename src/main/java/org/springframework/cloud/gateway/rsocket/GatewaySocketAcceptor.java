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

package org.springframework.cloud.gateway.rsocket;

import java.util.List;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.filter.AbstractFilterChain;
import org.springframework.cloud.gateway.rsocket.filter.RSocketExchange;
import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter;

public class GatewaySocketAcceptor implements SocketAcceptor {

	private final RSocket proxyRSocket;
	private final SocketAcceptorFilterChain filterChain;

	public GatewaySocketAcceptor(RSocket proxyRSocket, List<SocketAcceptorFilter> filters) {
		this.proxyRSocket = proxyRSocket;
		this.filterChain = new SocketAcceptorFilterChain(filters);
	}

	@Override
	public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {

		SocketAcceptorExchange exchange = new SocketAcceptorExchange(setup, sendingSocket);

		return this.filterChain.filter(exchange)
				.log("socket acceptor filter chain")
				.map(bool -> this.proxyRSocket);
	}

	public interface SocketAcceptorFilter extends RSocketFilter<SocketAcceptorExchange, SocketAcceptorFilterChain> {}

	public static class SocketAcceptorExchange implements RSocketExchange {
		private final ConnectionSetupPayload setup;
		private final RSocket sendingSocket;

		public SocketAcceptorExchange(ConnectionSetupPayload setup, RSocket sendingSocket) {
			this.setup = setup;
			this.sendingSocket = sendingSocket;
		}

		public ConnectionSetupPayload getSetup() {
			return setup;
		}

		public RSocket getSendingSocket() {
			return sendingSocket;
		}
	}

	public static class SocketAcceptorFilterChain
			extends AbstractFilterChain<SocketAcceptorFilter, SocketAcceptorExchange, SocketAcceptorFilterChain> {

		/**
		 * Public constructor with the list of filters and the target handler to use.
		 *
		 * @param filters the filters ahead of the handler
		 */
		public SocketAcceptorFilterChain(List<SocketAcceptorFilter> filters) {
			super(filters);
		}

		public SocketAcceptorFilterChain(List<SocketAcceptorFilter> allFilters, SocketAcceptorFilter currentFilter, SocketAcceptorFilterChain next) {
			super(allFilters, currentFilter, next);
		}

		@Override
		protected SocketAcceptorFilterChain create(List<SocketAcceptorFilter> allFilters, SocketAcceptorFilter currentFilter, SocketAcceptorFilterChain next) {
			return new SocketAcceptorFilterChain(allFilters, currentFilter, next);
		}
	}
}
