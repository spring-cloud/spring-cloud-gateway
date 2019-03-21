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

import java.util.Collections;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;

import org.springframework.cloud.gateway.rsocket.filter.AbstractRSocketExchange;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

public class SocketAcceptorExchange extends AbstractRSocketExchange {

	private final ConnectionSetupPayload setup;

	private final RSocket sendingSocket;

	private final Metadata metadata;

	public SocketAcceptorExchange(ConnectionSetupPayload setup, RSocket sendingSocket) {
		this(setup, sendingSocket, new Metadata(null, Collections.emptyMap()));
	}

	public SocketAcceptorExchange(ConnectionSetupPayload setup, RSocket sendingSocket,
			Metadata metadata) {
		this.setup = setup;
		this.sendingSocket = sendingSocket;
		this.metadata = metadata;
	}

	public ConnectionSetupPayload getSetup() {
		return setup;
	}

	public RSocket getSendingSocket() {
		return sendingSocket;
	}

	public Metadata getMetadata() {
		return metadata;
	}

}
