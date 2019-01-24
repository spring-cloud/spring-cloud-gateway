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

	private final String id;
	private final Registry registry;

	public GatewayRSocket(String id, Registry registry) {
		this.id = id;
		this.registry = registry;
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		log.debug("Entering requestChannel: " + id);
		return Flux.from(payloads)
				// .log("gateway rc b4son1")
				.switchOnFirst((signal, payloadFlux) -> {
					if (signal.hasValue()) {
						Payload payload = signal.get();

						RSocket rsocket = discover(payload);

						if (rsocket == null) {
							return Flux.empty();
						}
						return rsocket.requestChannel(payloadFlux);
					}
					return Flux.empty();
				});
	}

	private RSocket discover(Payload payload) {

		if (payload == null || !payload.hasMetadata()) { // and metadata is routing
			return null;
		}

		// TODO: deal with composite metadata

		List<String> tags = Metadata.decodeRouting(payload.sliceMetadata());

		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}

		log.debug("discovered service " + tags);

		RSocket rsocket = registry.find(tags);

		if (rsocket == null) {
			log.debug("Unable to find destination RSocket for " + tags);
		}
		// TODO: deal with connecting to cluster?

		// if not connected previously, initialize connection
		return rsocket;
	}
}

