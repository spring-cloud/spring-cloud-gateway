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

package org.springframework.cloud.gateway.rsocket.actuate;

import java.math.BigInteger;
import java.util.List;

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import org.springframework.cloud.gateway.rsocket.autoconfigure.BrokerProperties;
import org.springframework.cloud.gateway.rsocket.cluster.ClusterService;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class BrokerActuator {

	private static final Log log = LogFactory.getLog(BrokerActuator.class);

	/**
	 * Path for BrokerInfo actuator endpoint.
	 */
	public static final String BROKER_INFO_PATH = "actuator.gateway.brokerinfo";

	/**
	 * Path for RouteJoin actuator endpoint.
	 */
	public static final String ROUTE_JOIN_PATH = "actuator.gateway.routejoin";

	/**
	 * Path for RouteJoin actuator endpoint.
	 */
	public static final String ROUTE_REMOVE_PATH = "actuator.gateway.routeremove";

	private final BrokerProperties properties;

	private final ClusterService clusterService;

	private final RoutingTable routingTable;

	public BrokerActuator(BrokerProperties properties, ClusterService clusterService,
			RoutingTable routingTable) {
		this.properties = properties;
		this.clusterService = clusterService;
		this.routingTable = routingTable;
	}

	@MessageMapping("hello")
	public Mono<String> hello(String name) {
		return Mono.just("Hello " + name);
	}

	@MessageMapping(BROKER_INFO_PATH)
	public BigInteger brokerInfo(BrokerInfo brokerInfo) {
		log.info("BrokerInfo: " + brokerInfo);
		clusterService.registerIncoming(brokerInfo);
		return properties.getRouteId();
	}

	@MessageMapping(ROUTE_JOIN_PATH)
	@SuppressWarnings("Duplicates")
	public RouteJoin routeJoin(RouteJoin routeJoin) {
		log.info("RouteJoin: " + routeJoin);
		TagsMetadata findBrokerQuery = TagsMetadata.builder()
				.routeId(routeJoin.getBrokerId().toString()).build();
		List<Tuple2<String, RSocket>> rSockets = routingTable
				.findRSockets(findBrokerQuery);

		if (rSockets.size() != 1) {
			// should only be one broker
			if (log.isDebugEnabled()) {
				log.debug("Expected 1 RSocket for broker: " + routeJoin.getBrokerId()
						+ ", found " + rSockets.size());
			}
			return null;
		}
		RSocket brokerRSocket = rSockets.iterator().next().getT2();
		TagsMetadata.Builder tags = TagsMetadata.builder();
		// TODO: other tags.
		// routeJoin.getTags().forEach(tags::with);
		tags.routeId(routeJoin.getRouteId().toString())
				.serviceName(routeJoin.getServiceName());

		TagsMetadata tagsMetadata = tags.build();
		routingTable.register(tagsMetadata, brokerRSocket);

		brokerRSocket.onClose().doOnSuccess(v -> {
			if (log.isDebugEnabled()) {
				log.debug("Broker closed, deregistering " + tagsMetadata);
			}
			routingTable.deregister(tagsMetadata);
		}).doOnError(t -> {
			if (log.isErrorEnabled()) {
				log.error("Error received on broker, deregistering " + tagsMetadata, t);
			}
			routingTable.deregister(tagsMetadata);
		}).subscribe();
		// TODO: keep track of disposable?
		return routeJoin;
	}

	@MessageMapping(ROUTE_REMOVE_PATH)
	public boolean routeRemove(RouteRemove routeRemove) {
		log.info("RouteRemove: " + routeRemove);
		return routingTable.deregister(TagsMetadata.builder()
				.routeId(routeRemove.getRouteId().toString()).build());
	}

}
