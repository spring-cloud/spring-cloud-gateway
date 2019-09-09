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

package org.springframework.cloud.gateway.rsocket.core;

import java.util.Map;

import io.micrometer.core.instrument.Tags;
import io.rsocket.Payload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.Metadata;
import org.springframework.cloud.gateway.rsocket.filter.AbstractRSocketExchange;
import org.springframework.messaging.rsocket.MetadataExtractor;

/**
 * Exchange object used in GatewayFilterChain started by GatewayRSocket.
 */
public class GatewayExchange extends AbstractRSocketExchange {

	private static final Log log = LogFactory.getLog(GatewayExchange.class);

	/**
	 * Key for the route object in attributes.
	 */
	public static final String ROUTE_ATTR = "__route_attr_";

	public enum Type {

		/**
		 * RSocket fire and forget request type.
		 */
		FIRE_AND_FORGET("request.fnf"),

		/**
		 * RSocket request channel request type.
		 */
		REQUEST_CHANNEL("request.channel"),

		/**
		 * RSocket request response request type.
		 */
		REQUEST_RESPONSE("request.response"),

		/**
		 * RSocket request stream request type.
		 */
		REQUEST_STREAM("request.stream");

		private String key;

		Type(String key) {
			this.key = key;
		}

		String getKey() {
			return this.key;
		}

	}

	private final Type type;

	private final Forwarding routingMetadata;

	private Tags tags = Tags.empty();

	public static GatewayExchange fromPayload(Type type, Payload payload,
			MetadataExtractor metadataExtractor) {
		if (payload == null || !payload.hasMetadata()) {
			return null;
		}

		// TODO: deal with payload mimetype
		Map<String, Object> metadataMap = metadataExtractor.extract(payload,
				Metadata.COMPOSITE_MIME_TYPE);

		GatewayExchange exchange = new GatewayExchange(type,
				getForwardingMetadata(metadataMap));

		// TODO: custm metadata extractors
		// Adds routing metadata to exchange
		if (metadataMap.containsKey("route")) {
			exchange.getAttributes().put("route-metadata", metadataMap.get("route"));
		}

		return exchange;
	}

	private static Forwarding getForwardingMetadata(Map<String, Object> metadataMap) {
		if (metadataMap.containsKey("forwarding")) {
			Forwarding metadata = (Forwarding) metadataMap.get("forwarding");

			if (log.isDebugEnabled()) {
				log.debug("found routing metadata " + metadata);
			}
			return metadata;
		}

		return null;
	}

	public GatewayExchange(Type type, Forwarding routingMetadata) {
		this.type = type;
		this.routingMetadata = routingMetadata;
	}

	public Type getType() {
		return type;
	}

	public Forwarding getRoutingMetadata() {
		return routingMetadata;
	}

	public Tags getTags() {
		return this.tags;
	}

	public void setTags(Tags tags) {
		this.tags = tags;
	}

}
