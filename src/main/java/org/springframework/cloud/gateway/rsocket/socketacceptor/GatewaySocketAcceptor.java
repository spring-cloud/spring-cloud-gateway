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

package org.springframework.cloud.gateway.rsocket.socketacceptor;

import java.util.List;
import java.util.logging.Level;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import reactor.core.publisher.Mono;

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
				.log(GatewaySocketAcceptor.class.getName()+".socket acceptor filter chain", Level.FINE)
				.map(success -> this.proxyRSocket);
	}

}
