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

package org.springframework.cloud.gateway.rsocket.socketacceptor;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.core.GatewayRSocketFactory;
import org.springframework.cloud.gateway.rsocket.metrics.MicrometerResponderRSocket;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.util.MimeType;

public class GatewaySocketAcceptor implements SocketAcceptor {

	private static final Log log = LogFactory.getLog(GatewaySocketAcceptor.class);

	private final SocketAcceptorFilterChain filterChain;

	private final GatewayRSocketFactory rSocketFactory;

	private final MeterRegistry meterRegistry;

	private final GatewayRSocketProperties properties;

	private final MetadataExtractor metadataExtractor;

	public GatewaySocketAcceptor(GatewayRSocketFactory rSocketFactory,
			List<SocketAcceptorFilter> filters, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, MetadataExtractor metadataExtractor) {
		this.rSocketFactory = rSocketFactory;
		this.filterChain = new SocketAcceptorFilterChain(filters);
		this.meterRegistry = meterRegistry;
		this.properties = properties;
		this.metadataExtractor = metadataExtractor;
	}

	@Override
	@SuppressWarnings("Duplicates")
	public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
		if (log.isTraceEnabled()) {
			log.trace("accept()");
		}
		// decorate GatewayRSocket with metrics
		// current gateway id, type requester, service name (from metadata), service id

		Tags requesterTags = Tags.of("gateway.id", properties.getId(), "type",
				"requester");

		Tags metadataTags;
		SocketAcceptorExchange exchange;

		Map<String, Object> metadataMap = null;
		try {
			metadataMap = this.metadataExtractor.extract(setup,
					MimeType.valueOf(setup.metadataMimeType()));
		}
		catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Error extracting metadata", e);
			}
			return Mono.error(e);
		}
		if (metadataMap.containsKey("routesetup")) {
			RouteSetup metadata = (RouteSetup) metadataMap.get("routesetup");
			metadataTags = Tags.of("service.name", metadata.getServiceName())
					.and("service.id", metadata.getId().toString());
			// enrich exchange to have metadata
			exchange = new SocketAcceptorExchange(setup,
					decorate(sendingSocket, requesterTags.and(metadataTags)), metadata);
		}
		else {
			metadataTags = Tags.of("service.name", "UNKNOWN").and("service.id",
					"UNKNOWN");
			exchange = new SocketAcceptorExchange(setup,
					decorate(sendingSocket, requesterTags));
		}

		Tags responderTags = Tags
				.of("gateway.id", properties.getId(), "type", "responder")
				.and(metadataTags);

		// decorate with metrics gateway id, type responder, service name, service id
		// (instance id)
		return this.filterChain.filter(exchange).log(
				GatewaySocketAcceptor.class.getName() + ".socket acceptor filter chain",
				Level.FINEST).map(success -> {
					TagsMetadata tags = exchange.getMetadata().getEnrichedTagsMetadata();
					return decorate(this.rSocketFactory.create(tags), responderTags);
				});
	}

	private RSocket decorate(RSocket rSocket, Tags tags) {
		Tag[] tagArray = tags.stream().collect(Collectors.toList()).toArray(new Tag[] {});
		return new MicrometerResponderRSocket(rSocket, meterRegistry, tagArray);
	}

}
