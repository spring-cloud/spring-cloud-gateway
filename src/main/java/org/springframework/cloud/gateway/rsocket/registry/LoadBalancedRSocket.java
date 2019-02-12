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

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class LoadBalancedRSocket extends AbstractRSocket {

	private static final Log log = LogFactory.getLog(LoadBalancedRSocket.class);

	private final List<RSocket> delegates = new CopyOnWriteArrayList<>();

	private final Map<String, String> properties;

	private final LoadBalancer loadBalancer;

	public LoadBalancedRSocket(Map<String, String> properties) {
		this(properties, new RoundRobinLoadBalancer(properties));
	}

	public LoadBalancedRSocket(Map<String, String> properties, LoadBalancer loadBalancer) {
		this.properties = properties;
		this.loadBalancer = loadBalancer;
	}

	public Mono<RSocket> choose() {
		return this.loadBalancer.apply(this.delegates);
	}

	public void addRSocket(RSocket rsocket) {
		this.delegates.add(rsocket);
	}

	public List<RSocket> getDelegates() {
		return this.delegates;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return choose().flatMap(rSocket -> rSocket.fireAndForget(payload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return choose().flatMap(rSocket -> rSocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return choose().flatMapMany(rSocket -> rSocket.requestStream(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return choose().flatMapMany(rSocket -> rSocket.requestChannel(payloads));
	}

	@Override
	public Mono<Void> metadataPush(Payload payload) {
		return choose().flatMap(rSocket -> rSocket.metadataPush(payload));
	}

	//TODO: Flux<RSocket> as input?
	//TODO: reuse commons load balancer?
	public interface LoadBalancer extends Function<List<RSocket>, Mono<RSocket>> {
	}

	public static class RoundRobinLoadBalancer implements LoadBalancer {

		private final AtomicInteger position;
		private final Map<String, String> properties;

		public RoundRobinLoadBalancer(Map<String, String> properties) {
			this(properties, new Random().nextInt(1000));
		}

		public RoundRobinLoadBalancer(Map<String, String> properties, int seedPosition) {
			this.properties = properties;
			this.position = new AtomicInteger(seedPosition);
		}

		@Override
		public Mono<RSocket> apply(List<RSocket> rSockets) {
			if (rSockets.isEmpty()) {
				if (log.isWarnEnabled()) {
					log.warn("No servers available for: " + this.properties);
				}
				return Mono.empty();
			}
			// TODO: enforce order?
			int pos = Math.abs(this.position.incrementAndGet());

			RSocket rSocket = rSockets.get(pos % rSockets.size());
			return Mono.just(rSocket);
		}
	}
}
