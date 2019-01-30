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
	private final Function<List<String>, String> keyFunction;

	public Registry() {
		this(tags -> {
			if (CollectionUtils.isEmpty(tags)) {
				return null; // throw error?
			}
			//TODO: key generation
			return tags.get(0);
		});
	}

	public Registry(Function<List<String>, String> keyFunction) {
		this.keyFunction = keyFunction;
	}

	public String computeKey(List<String> tags) {
		return keyFunction.apply(tags);
	}

	public void register(List<String> tags, RSocket rsocket) {
		Assert.notEmpty(tags, "tags may not be empty");
		Assert.notNull(rsocket, "RSocket may not be null");
		log.debug("Registered RSocket: " + tags);
		LoadBalancedRSocket composite = rsockets.computeIfAbsent(computeKey(tags), s -> new LoadBalancedRSocket());
		composite.addRSocket(rsocket);
		registeredEventsSink.next(new RegisteredEvent(keyFunction, tags, rsocket));
	}

	public RSocket getRegistered(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}
		return rsockets.get(computeKey(tags));
	}

	public Disposable addListener(Consumer<RegisteredEvent> consumer) {
		return this.registeredEvents.subscribe(consumer);
	}

	public static class RegisteredEvent {
		private final Function<List<String>, String> keyFunction;
		private final List<String> routingMetadata;
		private final RSocket rSocket;

		public RegisteredEvent(Function<List<String>, String> keyFunction, List<String> routingMetadata,
							   RSocket rSocket) {
			Assert.notNull(keyFunction, "keyFunction may not be null");
			Assert.notEmpty(routingMetadata, "routingMetadata may not be empty");
			Assert.notNull(rSocket, "RSocket may not be null");
			this.keyFunction = keyFunction;
			this.routingMetadata = routingMetadata;
			this.rSocket = rSocket;
		}

		public List<String> getRoutingMetadata() {
			return routingMetadata;
		}

		public RSocket getRSocket() {
			return rSocket;
		}

		public boolean matches(List<String> otherRoutingMetadata) {
			if (!CollectionUtils.isEmpty(otherRoutingMetadata)) {
				String thisKey = keyFunction.apply(routingMetadata);
				String otherKey = keyFunction.apply(otherRoutingMetadata);
				return thisKey.equals(otherKey);
			}
			return false;
		}
	}
}
