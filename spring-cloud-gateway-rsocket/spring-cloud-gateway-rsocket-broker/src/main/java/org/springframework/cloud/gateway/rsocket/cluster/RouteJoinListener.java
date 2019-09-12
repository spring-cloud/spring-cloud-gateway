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

package org.springframework.cloud.gateway.rsocket.cluster;

import java.math.BigInteger;
import java.util.function.Consumer;

import org.springframework.cloud.gateway.rsocket.actuate.RouteJoin;
import org.springframework.cloud.gateway.rsocket.autoconfigure.BrokerProperties;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable.RegisteredEvent;

import static org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey.SERVICE_NAME;

public class RouteJoinListener implements Consumer<RegisteredEvent> {

	private final ClusterService clusterService;

	private final RoutingTable routingTable;

	private final BrokerProperties properties;

	public RouteJoinListener(ClusterService clusterService, RoutingTable routingTable,
			BrokerProperties properties) {
		this.clusterService = clusterService;
		this.routingTable = routingTable;
		this.properties = properties;
		routingTable.addListener(this);
	}

	@Override
	public void accept(RegisteredEvent registeredEvent) {
		BigInteger brokerId = properties.getRouteId();
		TagsMetadata tagsMetadata = registeredEvent.getRoutingMetadata();
		String serviceName = tagsMetadata.get(SERVICE_NAME);

		// Do not send RouteJoin requests for self
		if (!brokerId.toString().equals(tagsMetadata.getRouteId()) &&
		// or for other gateways yet
				!"gateway".equals(serviceName)) {
			BigInteger routeId = new BigInteger(tagsMetadata.getRouteId());
			RouteJoin routeJoin = RouteJoin.builder().brokerId(brokerId).routeId(routeId)
					.serviceName(serviceName).with(tagsMetadata).build();

			clusterService.send(routeJoin);
		}
	}

}
