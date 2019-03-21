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

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.rsocket.RSocket;
import io.rsocket.util.RSocketProxy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.support.Metadata;

public class LoadBalancedRSocket {

	private static final Log log = LogFactory.getLog(LoadBalancedRSocket.class);

	private final List<EnrichedRSocket> delegates = new CopyOnWriteArrayList<>();

	private final String serviceName;

	private final LoadBalancer loadBalancer;

	public LoadBalancedRSocket(String serviceName) {
		this(serviceName, new RoundRobinLoadBalancer(serviceName));
	}

	public LoadBalancedRSocket(String serviceName, LoadBalancer loadBalancer) {
		this.serviceName = serviceName;
		this.loadBalancer = loadBalancer;
	}

	public Mono<EnrichedRSocket> choose() {
		return this.loadBalancer.apply(this.delegates);
	}

	public void addRSocket(RSocket rsocket, Metadata metadata) {
		this.delegates.add(new EnrichedRSocket(rsocket, metadata));
	}

	public void remove(Metadata metadata) {
		// TODO: move delegates to a map for easy removal
		this.delegates.stream()
				.filter(enriched -> metadata.matches(enriched.getMetadata())).findFirst()
				.ifPresent(this.delegates::remove);
	}

	public List<EnrichedRSocket> getDelegates() {
		return this.delegates;
	}

	public static class EnrichedRSocket extends RSocketProxy {

		private final Metadata metadata;

		public EnrichedRSocket(RSocket source, Metadata metadata) {
			super(source);
			this.metadata = metadata;
		}

		public Metadata getMetadata() {
			return this.metadata;
		}

		public RSocket getSource() {
			return this.source;
		}

	}

	// TODO: Flux<RSocket> as input?
	// TODO: reuse commons load balancer?
	public interface LoadBalancer
			extends Function<List<EnrichedRSocket>, Mono<EnrichedRSocket>> {

	}

	public static class RoundRobinLoadBalancer implements LoadBalancer {

		private final AtomicInteger position;

		private final String serviceName;

		public RoundRobinLoadBalancer(String serviceName) {
			this(serviceName, new Random().nextInt(1000));
		}

		public RoundRobinLoadBalancer(String serviceName, int seedPosition) {
			this.serviceName = serviceName;
			this.position = new AtomicInteger(seedPosition);
		}

		@Override
		public Mono<EnrichedRSocket> apply(List<EnrichedRSocket> rSockets) {
			if (rSockets.isEmpty()) {
				if (log.isWarnEnabled()) {
					log.warn("No servers available for: " + this.serviceName);
				}
				return Mono.empty();
			}
			// TODO: enforce order?
			int pos = Math.abs(this.position.incrementAndGet());

			EnrichedRSocket rSocket = rSockets.get(pos % rSockets.size());
			return Mono.just(rSocket);
		}

	}

}
