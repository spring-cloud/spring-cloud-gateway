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

import org.springframework.cloud.gateway.rsocket.filter.AbstractFilterChain;
import org.springframework.cloud.gateway.rsocket.filter.RSocketExchange;
import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter;
import org.springframework.util.CollectionUtils;

public class GatewayRSocket extends AbstractRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;
	private final GatewayFilterChain filterChain;

	public GatewayRSocket(Registry registry, List<GatewayFilter> filters) {
		this.registry = registry;
		this.filterChain = new GatewayFilterChain(filters);
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return findRSocket(payload)
				.flatMap(rSocket -> rSocket.fireAndForget(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads)
				.switchOnFirst((signal, payloadFlux) -> {
					if (!signal.hasValue()) {
						return payloadFlux;
					}

					return findRSocket(signal.get())
							.flatMapMany(rSocket -> rSocket.requestChannel(payloadFlux));
				});
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return findRSocket(payload)
				.flatMap(rSocket -> rSocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return findRSocket(payload)
				.flatMapMany(rSocket -> rSocket.requestStream(payload));
	}

	private Mono<RSocket> findRSocket(Payload payload) {
		List<String> metadata = getRoutingMetadata(payload);
		RSocket service = findRSocket(metadata);

		if (service == null) {
			PendingRequestRSocket pending = new PendingRequestRSocket(metadata);
			this.registry.addListener(pending); //TODO: deal with removing?
			service = pending;
		}

		GatewayExchange exchange = new GatewayExchange(payload);

		RSocket rSocket = service;

		return this.filterChain.filter(exchange)
				.log("gateway filter chain")
				.filter(bool -> bool)
				.map(bool -> rSocket);
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


	public interface GatewayFilter extends RSocketFilter<GatewayExchange, GatewayFilterChain> {}

	public static class GatewayExchange implements RSocketExchange {
		private final Payload payload;

		public GatewayExchange(Payload payload) {
			this.payload = payload;
		}

		public Payload getPayload() {
			return payload;
		}
	}

	public static class GatewayFilterChain
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
}

