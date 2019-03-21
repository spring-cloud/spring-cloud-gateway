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

package org.springframework.cloud.gateway.rsocket.server;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.ResponderRSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.util.function.Tuple2;

import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter.Success;
import org.springframework.cloud.gateway.rsocket.registry.Registry.RegisteredEvent;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.ROUTE_ATTR;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_STREAM;
import static org.springframework.cloud.gateway.rsocket.server.GatewayFilterChain.executeFilterChain;

public class PendingRequestRSocket extends AbstractRSocket
		implements ResponderRSocket, Consumer<RegisteredEvent> {

	private static final Log log = LogFactory.getLog(PendingRequestRSocket.class);

	private final Function<RegisteredEvent, Mono<Route>> routeFinder;

	private final Consumer<Metadata> metadataCallback;

	private final MonoProcessor<RSocket> rSocketProcessor;

	private Disposable subscriptionDisposable;

	private Route route;

	public PendingRequestRSocket(Function<RegisteredEvent, Mono<Route>> routeFinder,
			Consumer<Metadata> metadataCallback) {
		this(routeFinder, metadataCallback, MonoProcessor.create());
	}

	/* for testing */ PendingRequestRSocket(
			Function<RegisteredEvent, Mono<Route>> routeFinder,
			Consumer<Metadata> metadataCallback,
			MonoProcessor<RSocket> rSocketProcessor) {
		this.routeFinder = routeFinder;
		this.metadataCallback = metadataCallback;
		this.rSocketProcessor = rSocketProcessor;
	}

	/**
	 * Find route (if needed) using pendingExchange. If found, see if the route target
	 * matches the registered service. If it matches, send registered RSocket to
	 * processor. Then execute normal filter chain. If filter chain is successful, execute
	 * request.
	 * @param registeredEvent the RegisteredEvent
	 */
	@Override
	public void accept(RegisteredEvent registeredEvent) {
		this.routeFinder.apply(registeredEvent).subscribe(route -> {
			this.route = route;
			this.metadataCallback.accept(registeredEvent.getRoutingMetadata());
			this.rSocketProcessor.onNext(registeredEvent.getRSocket());
			this.rSocketProcessor.onComplete();
			if (this.subscriptionDisposable != null) {
				this.subscriptionDisposable.dispose();
			}
		});
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return processor("pending-request-faf", payload)
				.flatMap(tuple -> tuple.getT1().fireAndForget(payload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return processor("pending-request-rr", payload)
				.flatMap(tuple -> tuple.getT1().requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return processor("pending-request-rs", payload)
				.flatMapMany(tuple -> tuple.getT1().requestStream(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
		return processor("pending-request-rc", payload).flatMapMany(tuple -> {
			RSocket rSocket = tuple.getT1();
			if (rSocket instanceof ResponderRSocket) {
				ResponderRSocket socket = (ResponderRSocket) rSocket;
				return socket.requestChannel(payload, payloads);
			}
			return rSocket.requestChannel(payloads);
		});
	}

	/**
	 * After processor receives onNext signal, get route from exchange attrs, create a new
	 * exchange from payload. Copy exchange attrs. Execute filter chain, if successful,
	 * execute request.
	 * @param logCategory log category
	 * @param payload payload.
	 * @return
	 */
	protected Mono<Tuple2<RSocket, Success>> processor(String logCategory,
			Payload payload) {
		return rSocketProcessor
				.log(PendingRequestRSocket.class.getName() + "." + logCategory,
						Level.FINEST)
				.flatMap(rSocket -> {
					GatewayExchange exchange = GatewayExchange.fromPayload(REQUEST_STREAM,
							payload);
					exchange.getAttributes().put(ROUTE_ATTR, route);
					// exchange.getAttributes().putAll(pendingExchange.getAttributes());
					return Mono.just(rSocket)
							.zipWith(executeFilterChain(route.getFilters(), exchange));
				});

	}

	public void setSubscriptionDisposable(Disposable subscriptionDisposable) {
		this.subscriptionDisposable = subscriptionDisposable;
	}

}
