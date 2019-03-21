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

package org.springframework.cloud.gateway.rsocket.autoconfigure;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.RSocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.core.env.Environment;

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

	// TODO: CompositeRoutes
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
	public GatewayRSocket.Factory gatewayRSocketFactory(Registry registry, Routes routes,
			MeterRegistry meterRegistry, GatewayRSocketProperties properties) {
		return new GatewayRSocket.Factory(registry, routes, meterRegistry, properties);
	}

	@Bean
	public GatewayRSocketProperties gatewayRSocketProperties(Environment env) {
		GatewayRSocketProperties properties = new GatewayRSocketProperties();
		if (env.containsProperty("spring.application.name")) {
			properties.setId(env.getProperty("spring.application.name")); // set default
																			// from env
		}
		return properties;
	}

	@Bean
	public SocketAcceptorPredicateFilter socketAcceptorPredicateFilter(
			List<SocketAcceptorPredicate> predicates) {
		return new SocketAcceptorPredicateFilter(predicates);
	}

	@Bean
	public GatewaySocketAcceptor socketAcceptor(GatewayRSocket.Factory rsocketFactory,
			List<SocketAcceptorFilter> filters, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties) {
		return new GatewaySocketAcceptor(rsocketFactory, filters, meterRegistry,
				properties);
	}

	@Bean
	public GatewayRSocketServer gatewayApp(GatewaySocketAcceptor socketAcceptor,
			GatewayRSocketProperties properties, MeterRegistry meterRegistry) {
		return new GatewayRSocketServer(properties, socketAcceptor, meterRegistry);
	}

}
