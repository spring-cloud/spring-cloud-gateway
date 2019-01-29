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

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

//TODO: name?
public class Registry {
	private static final Log log = LogFactory.getLog(Registry.class);

	private final Map<String, LoadBalancedRSocket> rsockets = new ConcurrentHashMap<>();

	private final EmitterProcessor<RegisteredEvent> registeredEvents = EmitterProcessor.create();

	public void register(List<String> tags, RSocket rsocket) {
		Assert.notEmpty(tags, "tags may not be empty");
		Assert.notNull(rsocket, "RSocket may not be null");
		log.debug("Registered RSocket: " + tags);
		LoadBalancedRSocket composite = rsockets.computeIfAbsent(tags.get(0), s -> new LoadBalancedRSocket());
		composite.addRSocket(rsocket);
		registeredEvents.onNext(new RegisteredEvent(tags, rsocket));
	}

	public RSocket getRegistered(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}
		return rsockets.get(tags.get(0));
	}

	public Disposable addListener(Consumer<RegisteredEvent> consumer) {
		return this.registeredEvents.subscribe(consumer);
	}

	public static class RegisteredEvent {
		private final List<String> routingMetadata;
		private final RSocket rSocket;

		public RegisteredEvent(List<String> routingMetadata, RSocket rSocket) {
			Assert.notEmpty(routingMetadata, "routingMetadata may not be empty");
			Assert.notNull(rSocket, "RSocket may not be null");
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
				//TODO: key generation
				return this.routingMetadata.get(0).equals(otherRoutingMetadata.get(0));
			}
			return false;
		}
	}
}
