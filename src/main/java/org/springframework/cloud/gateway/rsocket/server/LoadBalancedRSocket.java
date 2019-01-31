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

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class LoadBalancedRSocket extends AbstractRSocket {

	private final List<RSocket> delegates = new ArrayList<>(); //TODO: Concurrent?

	private final Function<List<RSocket>, RSocket> rsocketSelector;

	public LoadBalancedRSocket() {
		this(null);
	}

	// TODO: simple round robbin load balancing
	public LoadBalancedRSocket(Function<List<RSocket>, RSocket> rsocketSelector) {
		if (rsocketSelector != null) {
			this.rsocketSelector = rsocketSelector;
		} else {
			this.rsocketSelector = rSockets -> {
				if (!delegates.isEmpty()) {
					return delegates.get(0);
				}
				return null;
			};
		}
	}

	public RSocket choose() {
		RSocket rsocket = rsocketSelector.apply(delegates);
		if (rsocket == null) {
			throw new IllegalStateException("No RSockets available");
		}
		return rsocket;
	}

	public void addRSocket(RSocket rsocket) {
		this.delegates.add(rsocket);
	}

	public List<RSocket> getDelegates() {
		return delegates;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return choose().fireAndForget(payload);
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return choose().requestResponse(payload);
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return choose().requestStream(payload);
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return choose().requestChannel(payloads);
	}

	@Override
	public Mono<Void> metadataPush(Payload payload) {
		return choose().metadataPush(payload);
	}
}
