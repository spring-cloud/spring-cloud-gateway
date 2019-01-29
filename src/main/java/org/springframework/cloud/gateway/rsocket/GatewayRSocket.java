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

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.CollectionUtils;

public class GatewayRSocket extends AbstractRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;

	public GatewayRSocket(Registry registry) {
		this.registry = registry;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		RSocket service = findRSocket(payload);
		return service.fireAndForget(payload);
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads)
				.switchOnFirst((signal, payloadFlux) -> {
					if (!signal.hasValue()) {
						return payloadFlux;
					}

					RSocket service = findRSocket(signal.get());
					return service.requestChannel(payloadFlux);
				});
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		RSocket service = findRSocket(payload);
		return service.requestResponse(payload);
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		RSocket service = findRSocket(payload);
		return service.requestStream(payload);
	}

	private RSocket findRSocket(Payload payload) {
		List<String> metadata = getRoutingMetadata(payload);
		RSocket service = findRSocket(metadata);

		if (service == null) {
			PendingRequestRSocket pending = new PendingRequestRSocket(metadata);
			this.registry.addListener(pending); //TODO: deal with removing?
			service = pending;
		}

		return service;
	}

	private RSocket findRSocket(List<String> tags) {
		if (tags == null) return null; //TODO: error

		RSocket rsocket = registry.getRegistered(tags);

		if (rsocket == null) {
			log.debug("Unable to find destination RSocket for " + tags);
		}
		// TODO: deal with connecting to cluster?

		return rsocket;
	}

	private List<String> getRoutingMetadata(Payload payload) {
		if (payload == null || !payload.hasMetadata()) { // and metadata is routing
			return null;
		}

		// TODO: deal with composite metadata

		List<String> tags = Metadata.decodeRouting(payload.sliceMetadata());

		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}

		log.debug("found routing metadata " + tags);
		return tags;
	}
}

