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
import io.micrometer.core.instrument.Tags;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Convience class to hold and calculate exchange and metrics related information.
 */
public abstract class AbstractGatewayRSocket extends AbstractRSocket
		implements ResponderRSocket {

	private static final Log log = LogFactory.getLog(AbstractGatewayRSocket.class);

	protected final MeterRegistry meterRegistry;

	private final GatewayRSocketProperties properties;

	private final MetadataExtractor metadataExtractor;

	private final TagsMetadata metadata;

	AbstractGatewayRSocket(MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, MetadataExtractor metadataExtractor,
			TagsMetadata metadata) {
		this.meterRegistry = meterRegistry;
		this.properties = properties;
		this.metadataExtractor = metadataExtractor;
		this.metadata = metadata;
	}

	protected GatewayExchange createExchange(GatewayExchange.Type type, Payload payload) {
		GatewayExchange exchange = GatewayExchange.fromPayload(type, payload,
				metadataExtractor);
		Tags tags = getTags(exchange);
		exchange.setTags(tags);
		return exchange;
	}

	protected Tags getTags(GatewayExchange exchange) {
		// TODO: add tags to exchange
		String requesterName = "FIXME"; // FIXME: this.metadata.get(SERVICE_NAME);
		String requesterId = "FIXME"; // FIXME: this.metadata.getRouteId();
		String responderName = "FIXME"; // FIXME: exchange.getRoutingMetadata().getName();
		Assert.hasText(responderName, "responderName must not be empty");
		Assert.hasText(requesterId, "requesterId must not be empty");
		Assert.hasText(requesterName, "requesterName must not be empty");
		// responder.id happens in a callback, later
		return Tags.of("requester.name", requesterName, "responder.name", responderName,
				"requester.id", requesterId, "gateway.id", this.properties.getId());
	}

	protected void count(GatewayExchange exchange, String suffix) {
		count(exchange, suffix, Tags.empty());
	}

	protected void count(GatewayExchange exchange, Tags additionalTags) {
		count(exchange, null, additionalTags);
	}

	protected void count(GatewayExchange exchange, String suffix, Tags additionalTags) {
		Tags tags = exchange.getTags().and(additionalTags);
		String name = getMetricName(exchange, suffix);
		this.meterRegistry.counter(name, tags).increment();
	}

	protected String getMetricName(GatewayExchange exchange) {
		return getMetricName(exchange, null);
	}

	protected String getMetricName(GatewayExchange exchange, String suffix) {
		StringBuilder name = new StringBuilder("forward.");
		name.append(exchange.getType().getKey());
		if (StringUtils.hasLength(suffix)) {
			name.append(".");
			name.append(suffix);
		}
		return name.toString();
	}

}
