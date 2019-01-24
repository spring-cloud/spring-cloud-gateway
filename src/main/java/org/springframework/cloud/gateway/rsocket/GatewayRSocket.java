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
import reactor.core.publisher.MonoProcessor;

import org.springframework.util.CollectionUtils;

public class GatewayRSocket extends AbstractRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;

	public GatewayRSocket(Registry registry) {
		this.registry = registry;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		List<String> metadata = getRoutingMetadata(payload);
		RSocket service = findRSocket(metadata);

		if (service != null) {
			return service.fireAndForget(payload);
		}

		MonoProcessor<RSocket> processor = MonoProcessor.create();
		this.registry.pendingRequest(metadata, processor);

		return processor
				.flatMap(rSocket -> rSocket.fireAndForget(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads)
				.switchOnFirst((signal, payloadFlux) -> {
					if (!signal.hasValue()) {
						return payloadFlux;
					}

					Payload payload = signal.get();
					List<String> metadata = getRoutingMetadata(payload);
					RSocket service = findRSocket(metadata);

					if (service != null) {
						return service.requestChannel(payloadFlux);
					}

					MonoProcessor<RSocket> processor = MonoProcessor.create();
					this.registry.pendingRequest(metadata, processor);

					return processor
							.flatMapMany(rSocket -> rSocket.requestChannel(payloadFlux));
				});
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		List<String> metadata = getRoutingMetadata(payload);
		RSocket service = findRSocket(metadata);

		if (service != null) {
			return service.requestResponse(payload);
		}

		MonoProcessor<RSocket> processor = MonoProcessor.create();
		this.registry.pendingRequest(metadata, processor);

		return processor
				.flatMap(rSocket -> rSocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		List<String> metadata = getRoutingMetadata(payload);
		RSocket service = findRSocket(metadata);

		if (service != null) {
			return service.requestStream(payload);
		}

		MonoProcessor<RSocket> processor = MonoProcessor.create();
		this.registry.pendingRequest(metadata, processor);

		return processor
				.flatMapMany(rSocket -> rSocket.requestStream(payload));
	}

	private RSocket findRSocket(List<String> tags) {
		if (tags == null) return null;

		RSocket rsocket = registry.getRegistered(tags);

		if (rsocket == null) {
			log.debug("Unable to getRegistered destination RSocket for " + tags);
		}
		// TODO: deal with connecting to cluster?

		// if not connected previously, initialize connection
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

		log.debug("discovered service " + tags);
		return tags;
	}
}

