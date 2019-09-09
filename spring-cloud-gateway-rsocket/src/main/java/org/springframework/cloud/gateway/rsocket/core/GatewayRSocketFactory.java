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

package org.springframework.cloud.gateway.rsocket.core;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.routing.LoadBalancerFactory;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.util.Assert;

import static org.springframework.cloud.gateway.rsocket.metadata.WellKnownKey.ROUTE_ID;
import static org.springframework.cloud.gateway.rsocket.metadata.WellKnownKey.SERVICE_NAME;

public class GatewayRSocketFactory {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final RoutingTable routingTable;

	private final Routes routes;

	private final PendingRequestRSocketFactory pendingFactory;

	private final LoadBalancerFactory loadBalancerFactory;

	private final MeterRegistry meterRegistry;

	private final GatewayRSocketProperties properties;

	private final MetadataExtractor metadataExtractor;

	public GatewayRSocketFactory(RoutingTable routingTable, Routes routes,
			PendingRequestRSocketFactory pendingFactory,
			LoadBalancerFactory loadBalancerFactory, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, MetadataExtractor metadataExtractor) {
		this.routingTable = routingTable;
		this.routes = routes;
		this.pendingFactory = pendingFactory;
		this.loadBalancerFactory = loadBalancerFactory;
		this.meterRegistry = meterRegistry;
		this.properties = properties;
		this.metadataExtractor = metadataExtractor;
	}

	public GatewayRSocket create(TagsMetadata metadata) {
		Assert.hasText(metadata.get(ROUTE_ID), "metadata must contain " + ROUTE_ID);
		Assert.hasText(metadata.get(SERVICE_NAME),
				"metadata must contain " + SERVICE_NAME);

		GatewayRSocket gatewayRSocket = new GatewayRSocket(this.routes,
				this.pendingFactory, this.loadBalancerFactory, this.meterRegistry,
				this.properties, this.metadataExtractor, metadata);
		gatewayRSocket.onClose().doOnSuccess(v -> {
			if (log.isDebugEnabled()) {
				log.debug("Closed, deregistering " + metadata);
			}
			routingTable.deregister(metadata);
		}).doOnError(t -> {
			if (log.isErrorEnabled()) {
				log.error("Error received, deregistering " + metadata, t);
			}
			routingTable.deregister(metadata);
		}).doOnNext(v -> {
			if (log.isTraceEnabled()) {
				log.trace("OnClose doOnNext");
			}
		}).doOnTerminate(() -> {
			if (log.isTraceEnabled()) {
				log.trace("OnClose doOnTerminate");
			}
		}).doFinally(st -> {
			if (log.isTraceEnabled()) {
				log.trace("OnClose doFinally");
			}
		}).subscribe();
		return gatewayRSocket;
	}

}
