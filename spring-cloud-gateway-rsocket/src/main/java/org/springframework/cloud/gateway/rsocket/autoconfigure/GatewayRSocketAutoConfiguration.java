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

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.boot.rsocket.server.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServerFactory;
import org.springframework.cloud.gateway.rsocket.actuate.GatewayRSocketActuator;
import org.springframework.cloud.gateway.rsocket.actuate.GatewayRSocketActuatorRegistrar;
import org.springframework.cloud.gateway.rsocket.core.GatewayRSocketFactory;
import org.springframework.cloud.gateway.rsocket.core.GatewayServerRSocketFactoryCustomizer;
import org.springframework.cloud.gateway.rsocket.core.PendingRequestRSocketFactory;
import org.springframework.cloud.gateway.rsocket.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.metadata.RouteSetup;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.routing.LoadBalancerFactory;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTableRoutes;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTableSocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.GatewaySocketAcceptor;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicate;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicateFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;

import static org.springframework.cloud.gateway.rsocket.metadata.Forwarding.FORWARDING_MIME_TYPE;
import static org.springframework.cloud.gateway.rsocket.metadata.RouteSetup.ROUTE_SETUP_MIME_TYPE;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass(RSocket.class)
@AutoConfigureBefore(RSocketServerAutoConfiguration.class)
public class GatewayRSocketAutoConfiguration {

	@Bean
	public RoutingTable routingTable() {
		return new RoutingTable();
	}

	// TODO: CompositeRoutes
	@Bean
	public RoutingTableRoutes registryRoutes(RoutingTable routingTable) {
		return new RoutingTableRoutes(routingTable);
	}

	@Bean
	public RoutingTableSocketAcceptorFilter registrySocketAcceptorFilter(
			RoutingTable routingTable) {
		return new RoutingTableSocketAcceptorFilter(routingTable);
	}

	@Bean
	public PendingRequestRSocketFactory pendingRequestRSocketFactory(
			RoutingTable routingTable, Routes routes,
			RSocketStrategies rSocketStrategies) {
		return new PendingRequestRSocketFactory(routingTable, routes,
				rSocketStrategies.metadataExtractor());
	}

	@Bean
	public LoadBalancerFactory loadBalancerFactory(RoutingTable routingTable) {
		return new LoadBalancerFactory(routingTable);
	}

	@Bean
	public GatewayRSocketFactory gatewayRSocketFactory(RoutingTable routingTable,
			Routes routes, PendingRequestRSocketFactory pendingFactory,
			LoadBalancerFactory loadBalancerFactory, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, RSocketStrategies rSocketStrategies) {
		return new GatewayRSocketFactory(routingTable, routes, pendingFactory,
				loadBalancerFactory, meterRegistry, properties,
				rSocketStrategies.metadataExtractor());
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
	public GatewaySocketAcceptor socketAcceptor(GatewayRSocketFactory rsocketFactory,
			List<SocketAcceptorFilter> filters, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, RSocketStrategies rSocketStrategies) {
		MetadataExtractor metadataExtractor = registerMimeTypes(rSocketStrategies);
		return new GatewaySocketAcceptor(rsocketFactory, filters, meterRegistry,
				properties, metadataExtractor);
	}

	public static MetadataExtractor registerMimeTypes(
			RSocketStrategies rSocketStrategies) {
		MetadataExtractor metadataExtractor = rSocketStrategies.metadataExtractor();
		// TODO: see if possible to make easier in framework.
		if (metadataExtractor instanceof DefaultMetadataExtractor) {
			DefaultMetadataExtractor extractor = (DefaultMetadataExtractor) metadataExtractor;
			extractor.metadataToExtract(FORWARDING_MIME_TYPE, Forwarding.class,
					Forwarding.METADATA_KEY);
			extractor.metadataToExtract(ROUTE_SETUP_MIME_TYPE, RouteSetup.class,
					RouteSetup.METADATA_KEY);
		}
		return metadataExtractor;
	}

	@Bean
	public GatewayServerRSocketFactoryCustomizer gatewayServerRSocketFactoryCustomizer(
			GatewayRSocketProperties properties, MeterRegistry meterRegistry) {
		return new GatewayServerRSocketFactoryCustomizer(properties, meterRegistry);
	}

	@Bean
	public RSocketServerBootstrap gatewayRSocketServerBootstrap(
			RSocketServerFactory rSocketServerFactory,
			GatewaySocketAcceptor gatewaySocketAcceptor) {
		return new RSocketServerBootstrap(rSocketServerFactory, gatewaySocketAcceptor);
	}

	@Bean
	public RSocketStrategiesCustomizer gatewayRSocketStrategiesCustomizer() {
		return strategies -> {
			strategies.decoder(new Forwarding.Decoder(), new RouteSetup.Decoder())
					.encoder(new Forwarding.Encoder(), new RouteSetup.Encoder());
		};
	}

	@Bean
	public GatewayRSocketActuatorRegistrar gatewayRSocketActuatorRegistrar(
			RoutingTable routingTable, RSocketMessageHandler messageHandler,
			GatewayRSocketProperties properties) {
		return new GatewayRSocketActuatorRegistrar(routingTable, messageHandler,
				properties);
	}

	@Bean
	public GatewayRSocketActuator gatwayRSocketActuator() {
		return new GatewayRSocketActuator();
	}

}
