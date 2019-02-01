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

package org.springframework.cloud.gateway.rsocket.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.rsocket.RSocket;

import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.server.GatewayFilter;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocketServer;
import org.springframework.cloud.gateway.rsocket.socketacceptor.GatewaySocketAcceptor;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.registry.RegistrySocketAcceptorFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled", matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass(RSocket.class)
public class GatewayRSocketAutoConfiguration {

	@Bean
	public Registry registry() {
		return new Registry();
	}

	@Bean
	public RegistrySocketAcceptorFilter registrySocketAcceptorFilter(Registry registry) {
		return new RegistrySocketAcceptorFilter(registry);
	}

	@Bean
	public GatewayRSocket gatewayRSocket(Registry registry, List<GatewayFilter> filters) {
		return new GatewayRSocket(registry, filters);
	}

	@Bean
	public GatewayRSocketProperties gatewayRSocketProperties() {
		return new GatewayRSocketProperties();
	}

	@Bean
	public GatewaySocketAcceptor socketAcceptor(GatewayRSocket rsocket, List<SocketAcceptorFilter> filters) {
		return new GatewaySocketAcceptor(rsocket, filters);
	}

	@Configuration
	@ConditionalOnClass({ MeterRegistry.class, MicrometerRSocketInterceptor.class })
	protected static class GatewayRSocketServerMicrometerConfiguration {

		private final GatewayRSocketProperties properties;

		public GatewayRSocketServerMicrometerConfiguration(GatewayRSocketProperties properties) {
			this.properties = properties;
		}

		@Bean
		public MicrometerRSocketInterceptor micrometerRSocketInterceptor(MeterRegistry meterRegistry) {
			Tags tags = Tags.of(properties.getServer().getMicrometerTags().toArray(new String[]{}));
			List<Tag> tagList = tags.stream().collect(Collectors.toList());
			return new MicrometerRSocketInterceptor(meterRegistry, tagList.toArray(new Tag[]{}));
		}

		@Bean
		public GatewayRSocketServer gatewayApp(GatewaySocketAcceptor socketAcceptor,
											   MicrometerRSocketInterceptor micrometerInterceptor) {
			return new GatewayRSocketServer(properties, socketAcceptor, micrometerInterceptor);
		}

	}

	@Configuration
	@ConditionalOnMissingClass({ "io.micrometer.core.instrument.MeterRegistry", "io.rsocket.micrometer.MicrometerRSocketInterceptor" })
	protected static class GatewayRSocketServerConfiguration {

		@Bean
		public GatewayRSocketServer gatewayApp(GatewayRSocketProperties properties, GatewaySocketAcceptor socketAcceptor) {
			return new GatewayRSocketServer(properties, socketAcceptor);
		}

	}
}
