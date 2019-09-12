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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.gateway.rsocket.actuate.BrokerActuator;
import org.springframework.cloud.gateway.rsocket.actuate.BrokerInfo;
import org.springframework.cloud.gateway.rsocket.actuate.RouteJoin;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.core.style.ToStringCreator;
import org.springframework.messaging.rsocket.RSocketRequester;

public class ClusterService {

	final Map<String, BrokerEntry> incomingBrokers = new ConcurrentHashMap<>();

	final Map<String, RSocketRequester> outgoingBrokers = new ConcurrentHashMap<>();

	public boolean registerIncoming(BrokerInfo brokerInfo) {
		String brokerId = brokerInfo.getBrokerId().toString();

		// TODO: validate that there is a corresponding route in RoutingTable
		if (incomingBrokers.containsKey(brokerId)) {
			BrokerEntry brokerEntry = incomingBrokers.get(brokerId);
			if (brokerEntry.timestamp < brokerInfo.getTimestamp()) {
				incomingBrokers.put(brokerId, new BrokerEntry(brokerInfo.getBrokerId(),
						brokerInfo.getTags(), brokerInfo.getTimestamp()));
				return true;
			}
		}
		else {
			incomingBrokers.put(brokerId, new BrokerEntry(brokerInfo.getBrokerId(),
					brokerInfo.getTags(), brokerInfo.getTimestamp()));
			return true;
		}
		return false;
	}

	public boolean registerOutgoing(String routeId, RSocketRequester requester) {
		// TODO: BrokerClient instead of RSocketRequester?
		// TODO: validation
		outgoingBrokers.put(routeId, requester);
		return true;
	}

	public boolean send(RouteJoin routeJoin) {
		outgoingBrokers.values().forEach(requester -> {
			Forwarding forwarding = Forwarding.of(routeJoin.getRouteId())
					.serviceName("gateway").disableProxy().build();
			requester.route(BrokerActuator.ROUTE_JOIN_PATH)
					.metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE).data(routeJoin)
					.retrieveMono(RouteJoin.class)
					.subscribe(res -> System.out.println("RouteJoin: " + res));
		});
		return true;
	}

	static class BrokerEntry {

		private final BigInteger brokerId;

		private final Map<TagsMetadata.Key, String> tags;

		private final Long timestamp;

		BrokerEntry(BigInteger brokerId, Map<TagsMetadata.Key, String> tags,
				Long timestamp) {
			this.brokerId = brokerId;
			this.tags = tags;
			this.timestamp = timestamp;
		}

		public BigInteger getBrokerId() {
			return this.brokerId;
		}

		public Map<TagsMetadata.Key, String> getTags() {
			return this.tags;
		}

		public Long getTimestamp() {
			return this.timestamp;
		}

		@Override
		public String toString() {
			// @formatter:off
			return new ToStringCreator(this)
					.append("brokerId", brokerId)
					.append("tags", tags)
					.append("timestamp", timestamp)
					.toString();
			// @formatter:on
		}

	}

}
