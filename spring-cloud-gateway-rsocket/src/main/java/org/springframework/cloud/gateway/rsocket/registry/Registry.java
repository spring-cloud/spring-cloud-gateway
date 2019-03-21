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

package org.springframework.cloud.gateway.rsocket.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.Disposable;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;

import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.util.Assert;

/**
 * The Registry handles all RSocket connections that have been made that have associated
 * announcement metadata. RSocket connections can then be found based on routing metadata.
 * When a new RSocket is registered, a RegisteredEvent is pushed onto a DirectProcessor
 * that is acting as an event bus for registered Consumers.
 */
// TODO: name?
public class Registry {

	private static final Log log = LogFactory.getLog(Registry.class);

	private final Map<String, LoadBalancedRSocket> rsockets = new ConcurrentHashMap<>();

	private final DirectProcessor<RegisteredEvent> registeredEvents = DirectProcessor
			.create();

	private final FluxSink<RegisteredEvent> registeredEventsSink = registeredEvents
			.sink(FluxSink.OverflowStrategy.DROP);

	public Registry() {
	}

	// TODO: Mono<Void>?
	public void register(Metadata metadata, RSocket rsocket) {
		Assert.notNull(metadata, "metadata may not be null");
		Assert.notNull(rsocket, "RSocket may not be null");
		if (log.isDebugEnabled()) {
			log.debug("Registering RSocket: " + metadata);
		}
		LoadBalancedRSocket composite = rsockets.computeIfAbsent(metadata.getName(),
				s -> new LoadBalancedRSocket(metadata.getName()));
		composite.addRSocket(rsocket, metadata);
		registeredEventsSink.next(new RegisteredEvent(metadata, rsocket));
	}

	public void deregister(Metadata metadata) {
		Assert.notNull(metadata, "metadata may not be null");
		if (log.isDebugEnabled()) {
			log.debug("Deregistering RSocket: " + metadata);
		}
		LoadBalancedRSocket loadBalanced = this.rsockets.get(metadata.getName());
		if (loadBalanced != null) {
			loadBalanced.remove(metadata);
		}
	}

	public LoadBalancedRSocket getRegistered(Metadata metadata) {
		return rsockets.get(metadata.getName());
	}

	public Disposable addListener(Consumer<RegisteredEvent> consumer) {
		return this.registeredEvents.subscribe(consumer);
	}

	public static class RegisteredEvent {

		private final Metadata routingMetadata;

		private final RSocket rSocket;

		public RegisteredEvent(Metadata routingMetadata, RSocket rSocket) {
			Assert.notNull(routingMetadata, "routingMetadata may not be null");
			Assert.notNull(rSocket, "RSocket may not be null");
			this.routingMetadata = routingMetadata;
			this.rSocket = rSocket;
		}

		public Metadata getRoutingMetadata() {
			return routingMetadata;
		}

		public RSocket getRSocket() {
			return rSocket;
		}

	}

}
