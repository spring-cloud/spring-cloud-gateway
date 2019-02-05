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

package org.springframework.cloud.gateway.rsocket.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.Disposable;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * The Registry handles all RSocket connections that have been made that have associated
 * announcement metadata. RSocket connections can then be found based on routing metadata.
 * When a new RSocket is registered, a RegisteredEvent is pushed onto a DirectProcessor
 * that is acting as an event bus for registered Consumers.
 */
//TODO: name?
public class Registry {
	private static final Log log = LogFactory.getLog(Registry.class);

	private final Map<String, LoadBalancedRSocket> rsockets = new ConcurrentHashMap<>();

	private final DirectProcessor<RegisteredEvent> registeredEvents = DirectProcessor.create();
	private final FluxSink<RegisteredEvent> registeredEventsSink = registeredEvents.sink(FluxSink.OverflowStrategy.DROP);
	private final Function<Map<String, String>, String> keyFunction;

	public Registry() {
		this(tags -> {
			if (CollectionUtils.isEmpty(tags)) {
				return null; // throw error?
			}
			//TODO: key generation
			return tags.get("name");
		});
	}

	public Registry(Function<Map<String, String>, String> keyFunction) {
		this.keyFunction = keyFunction;
	}

	public String computeKey(Map<String, String> properties) {
		return keyFunction.apply(properties);
	}

	//TODO: Mono<Void>?
	public void register(Map<String, String> properties, RSocket rsocket) {
		Assert.notEmpty(properties, "properties may not be empty");
		Assert.notNull(rsocket, "RSocket may not be null");
		log.debug("Registered RSocket: " + properties);
		LoadBalancedRSocket composite = rsockets.computeIfAbsent(computeKey(properties), s -> new LoadBalancedRSocket());
		composite.addRSocket(rsocket);
		registeredEventsSink.next(new RegisteredEvent(properties, rsocket));
	}

	public RSocket getRegistered(Map<String, String> properties) {
		if (CollectionUtils.isEmpty(properties)) {
			return null;
		}
		return rsockets.get(computeKey(properties));
	}

	public Disposable addListener(Consumer<RegisteredEvent> consumer) {
		return this.registeredEvents.subscribe(consumer);
	}

	public static class RegisteredEvent {
		private final Map<String, String> routingMetadata;
		private final RSocket rSocket;

		public RegisteredEvent(Map<String, String> routingMetadata, RSocket rSocket) {
			Assert.notEmpty(routingMetadata, "routingMetadata may not be empty");
			Assert.notNull(rSocket, "RSocket may not be null");
			this.routingMetadata = routingMetadata;
			this.rSocket = rSocket;
		}

		public Map<String, String> getRoutingMetadata() {
			return routingMetadata;
		}

		public RSocket getRSocket() {
			return rSocket;
		}

	}
}
