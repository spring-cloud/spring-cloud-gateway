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
import java.util.logging.Level;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.metrics.MicrometerResponderRSocket;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

public class GatewaySocketAcceptor implements SocketAcceptor {

	private final SocketAcceptorFilterChain filterChain;

	private final GatewayRSocket.Factory rSocketFactory;

	private final MeterRegistry meterRegistry;

	private final GatewayRSocketProperties properties;

	public GatewaySocketAcceptor(GatewayRSocket.Factory rSocketFactory,
			List<SocketAcceptorFilter> filters, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties) {
		this.rSocketFactory = rSocketFactory;
		this.filterChain = new SocketAcceptorFilterChain(filters);
		this.meterRegistry = meterRegistry;
		this.properties = properties;
	}

	@Override
	@SuppressWarnings("Duplicates")
	public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {

		// decorate GatewayRSocket with metrics
		// current gateway id, type requester, service name (from metadata), service id

		Tags requesterTags = Tags.of("gateway.id", properties.getId(), "type",
				"requester");

		Tags metadataTags;
		SocketAcceptorExchange exchange;
		if (setup.hasMetadata()) { // TODO: and setup.metadataMimeType() is Announcement
									// metadata or composite
			Metadata metadata = Metadata.decodeMetadata(setup.sliceMetadata());
			metadataTags = Tags.of("service.name", metadata.getName()).and("service.id",
					metadata.get("id"));
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
		return this.filterChain.filter(exchange)
				.log(GatewaySocketAcceptor.class.getName()
						+ ".socket acceptor filter chain", Level.FINEST)
				.map(success -> decorate(
						this.rSocketFactory.create(exchange.getMetadata()),
						responderTags));
	}

	private RSocket decorate(RSocket rSocket, Tags tags) {
		Tag[] tagArray = tags.stream().collect(Collectors.toList()).toArray(new Tag[] {});
		return new MicrometerResponderRSocket(rSocket, meterRegistry, tagArray);
	}

}
