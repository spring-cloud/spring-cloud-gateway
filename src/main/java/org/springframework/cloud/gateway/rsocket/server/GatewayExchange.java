/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.rsocket.server;

import java.util.Map;

import io.micrometer.core.instrument.Tags;
import io.rsocket.Payload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.rsocket.filter.AbstractRSocketExchange;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.util.CollectionUtils;

public class GatewayExchange extends AbstractRSocketExchange {

	private static final Log log = LogFactory.getLog(GatewayExchange.class);
	public static final String ROUTE_ATTR = "__route_attr_";

	enum Type {
		FIRE_AND_FORGET("request.fnf"),
		REQUEST_CHANNEL("request.channel"),
		REQUEST_RESPONSE("request.response"),
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
	private final Map<String, String> routingMetadata;
	private Tags tags = Tags.empty();

	public static GatewayExchange fromPayload(Type type, Payload payload) {
		return new GatewayExchange(type, getRoutingMetadata(payload));
	}

	private static Map<String, String> getRoutingMetadata(Payload payload) {
		if (payload == null || !payload.hasMetadata()) { // and metadata is routing
			return null;
		}

		// TODO: deal with composite metadata

		Map<String, String> properties = Metadata.decodeProperties(payload.sliceMetadata());

		if (CollectionUtils.isEmpty(properties)) {
			return null;
		}

		if (log.isDebugEnabled()) {
			log.debug("found routing metadata " + properties);
		}
		return properties;
	}

	public GatewayExchange(Type type, Map<String, String> routingMetadata) {
		this.type = type;
		this.routingMetadata = routingMetadata;
	}

	public Type getType() {
		return type;
	}

	public Map<String, String> getRoutingMetadata() {
		return routingMetadata;
	}

	public Tags getTags() {
		return this.tags;
	}

	public void setTags(Tags tags) {
		this.tags = tags;
	}
}
