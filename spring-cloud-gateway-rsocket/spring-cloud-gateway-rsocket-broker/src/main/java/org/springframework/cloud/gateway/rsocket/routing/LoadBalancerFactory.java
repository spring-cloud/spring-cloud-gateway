/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.routing;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;

public class LoadBalancerFactory {

	private static final Log log = LogFactory.getLog(LoadBalancerFactory.class);

	private final RoutingTable routingTable;

	public LoadBalancerFactory(RoutingTable routingTable) {
		this.routingTable = routingTable;
	}

	// TODO: potentially GatewayExchange or return a new Result Object?
	public Mono<Tuple2<String, RSocket>> choose(TagsMetadata tagsMetadata) {
		List<Tuple2<String, RSocket>> rSockets = this.routingTable
				.findRSockets(tagsMetadata);
		// TODO: change loadbalancer impl based on tags
		// TODO: cache loadbalancers based on tags
		return new RoundRobinLoadBalancer(tagsMetadata).apply(rSockets);
	}

	// TODO: Flux<RSocket> as input?
	// TODO: reuse commons load balancer?
	public interface LoadBalancer extends
			Function<List<Tuple2<String, RSocket>>, Mono<Tuple2<String, RSocket>>> {

	}

	public static class RoundRobinLoadBalancer implements LoadBalancer {

		private final TagsMetadata tagsMetadata;

		private final AtomicInteger position;

		public RoundRobinLoadBalancer(TagsMetadata tagsMetadata) {
			this(tagsMetadata, new Random().nextInt(1000));
		}

		public RoundRobinLoadBalancer(TagsMetadata tagsMetadata, int seedPosition) {
			this.tagsMetadata = tagsMetadata;
			this.position = new AtomicInteger(seedPosition);
		}

		@Override
		public Mono<Tuple2<String, RSocket>> apply(
				List<Tuple2<String, RSocket>> rSockets) {
			if (rSockets.isEmpty()) {
				if (log.isWarnEnabled()) {
					log.warn("No servers available for: " + this.tagsMetadata);
				}
				return Mono.empty();
			}
			// TODO: enforce order?
			int pos = Math.abs(this.position.incrementAndGet());

			Tuple2<String, RSocket> tuple = rSockets.get(pos % rSockets.size());
			return Mono.just(tuple);
		}

	}

}
