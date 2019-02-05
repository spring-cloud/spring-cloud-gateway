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

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.rsocket.metrics.MicrometerResponderRSocketInterceptor;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.registry.RegistryRoutes;
import org.springframework.cloud.gateway.rsocket.registry.RegistrySocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocketServer;
import org.springframework.cloud.gateway.rsocket.socketacceptor.GatewaySocketAcceptor;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicate;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicateFilter;
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

	//TODO: CompositeRoutes
	@Bean
	public RegistryRoutes registryRoutes(Registry registry) {
		RegistryRoutes registryRoutes = new RegistryRoutes();
		registry.addListener(registryRoutes);
		return registryRoutes;
	}

	@Bean
	public RegistrySocketAcceptorFilter registrySocketAcceptorFilter(Registry registry) {
		return new RegistrySocketAcceptorFilter(registry);
	}

	@Bean
	public GatewayRSocket gatewayRSocket(Registry registry, Routes routes) {
		return new GatewayRSocket(registry, routes);
	}

	@Bean
	public GatewayRSocketProperties gatewayRSocketProperties() {
		return new GatewayRSocketProperties();
	}

	@Bean
	public SocketAcceptorPredicateFilter socketAcceptorPredicateFilter(List<SocketAcceptorPredicate> predicates) {
		return new SocketAcceptorPredicateFilter(predicates);
	}

	@Bean
	public GatewaySocketAcceptor socketAcceptor(GatewayRSocket rsocket, List<SocketAcceptorFilter> filters) {
		return new GatewaySocketAcceptor(rsocket, filters);
	}

	@Configuration
	@ConditionalOnClass(MeterRegistry.class)
	protected static class GatewayRSocketServerMicrometerConfiguration {

		private final GatewayRSocketProperties properties;

		public GatewayRSocketServerMicrometerConfiguration(GatewayRSocketProperties properties) {
			this.properties = properties;
		}

		@Bean
		public MicrometerResponderRSocketInterceptor micrometerResponderRSocketInterceptor(MeterRegistry meterRegistry) {
			Tags tags = Tags.of(properties.getServer().getMicrometerTags().toArray(new String[]{}));
			List<Tag> tagList = tags.stream().collect(Collectors.toList());
			return new MicrometerResponderRSocketInterceptor(meterRegistry, tagList.toArray(new Tag[]{}));
		}

		@Bean
		public GatewayRSocketServer gatewayApp(GatewaySocketAcceptor socketAcceptor,
											   MicrometerResponderRSocketInterceptor micrometerInterceptor) {
			return new GatewayRSocketServer(properties, socketAcceptor, micrometerInterceptor);
		}

	}

	@Configuration
	@ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
	protected static class GatewayRSocketServerConfiguration {

		@Bean
		public GatewayRSocketServer gatewayApp(GatewayRSocketProperties properties, GatewaySocketAcceptor socketAcceptor) {
			return new GatewayRSocketServer(properties, socketAcceptor);
		}

	}
}
