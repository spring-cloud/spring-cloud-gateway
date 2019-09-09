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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.rsocket.RSocketFactory.ServerRSocketFactory;
import io.rsocket.micrometer.MicrometerDuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.rsocket.server.ServerRSocketFactoryCustomizer;
import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.util.Assert;

public class GatewayServerRSocketFactoryCustomizer
		implements ServerRSocketFactoryCustomizer {

	private static final Log log = LogFactory
			.getLog(GatewayServerRSocketFactoryCustomizer.class);

	private static final RSocketInterceptor[] EMPTY_INTERCEPTORS = new RSocketInterceptor[0];

	private final GatewayRSocketProperties properties;

	private final List<RSocketInterceptor> serverInterceptors;

	private final MeterRegistry meterRegistry;

	public GatewayServerRSocketFactoryCustomizer(GatewayRSocketProperties properties,
			MeterRegistry meterRegistry) {
		this(properties, meterRegistry, EMPTY_INTERCEPTORS);
	}

	public GatewayServerRSocketFactoryCustomizer(GatewayRSocketProperties properties,
			MeterRegistry meterRegistry, RSocketInterceptor... interceptors) {
		Assert.notNull(properties, "properties may not be null");
		Assert.notNull(meterRegistry, "meterRegistry may not be null");
		Assert.notNull(interceptors, "interceptors may not be null");
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.serverInterceptors = Arrays.asList(interceptors);
	}

	@Override
	public ServerRSocketFactory apply(ServerRSocketFactory factory) {
		serverInterceptors.forEach(factory::addResponderPlugin);

		List<String> micrometerTags = properties.getMicrometerTags();
		Tag[] tags = Tags.of(micrometerTags.toArray(new String[] {}))
				.and("gateway.id", properties.getId()).stream()
				.collect(Collectors.toList()).toArray(new Tag[] {});

		return factory
				// TODO: add as bean like serverInterceptors above
				.addConnectionPlugin(
						new MicrometerDuplexConnectionInterceptor(meterRegistry, tags))
				.errorConsumer(throwable -> {
					if (log.isDebugEnabled()) {
						log.debug("Error with connection", throwable);
					}
				}); // TODO: add configurable errorConsumer
	}

}
