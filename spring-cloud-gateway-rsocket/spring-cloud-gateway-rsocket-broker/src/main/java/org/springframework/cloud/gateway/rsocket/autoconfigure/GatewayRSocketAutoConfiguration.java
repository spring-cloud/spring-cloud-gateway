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

import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.RSocket;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.server.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServerFactory;
import org.springframework.cloud.gateway.rsocket.actuate.BrokerActuator;
import org.springframework.cloud.gateway.rsocket.actuate.BrokerActuatorHandlerRegistration;
import org.springframework.cloud.gateway.rsocket.cluster.ClusterJoinListener;
import org.springframework.cloud.gateway.rsocket.cluster.ClusterService;
import org.springframework.cloud.gateway.rsocket.cluster.RouteJoinListener;
import org.springframework.cloud.gateway.rsocket.common.autoconfigure.GatewayRSocketCommonAutoConfiguration;
import org.springframework.cloud.gateway.rsocket.core.GatewayRSocketFactory;
import org.springframework.cloud.gateway.rsocket.core.GatewayServerRSocketFactoryCustomizer;
import org.springframework.cloud.gateway.rsocket.core.PendingRequestRSocketFactory;
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
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;

import static org.springframework.cloud.gateway.rsocket.common.autoconfigure.GatewayRSocketCommonAutoConfiguration.ID_GENERATOR_BEAN_NAME;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass(RSocket.class)
@AutoConfigureBefore(RSocketServerAutoConfiguration.class)
@AutoConfigureAfter(GatewayRSocketCommonAutoConfiguration.class)
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
			BrokerProperties properties, RSocketStrategies rSocketStrategies) {
		return new GatewayRSocketFactory(routingTable, routes, pendingFactory,
				loadBalancerFactory, meterRegistry, properties,
				rSocketStrategies.metadataExtractor());
	}

	@Bean
	public BrokerProperties brokerProperties(Environment env,
			@Qualifier(ID_GENERATOR_BEAN_NAME) Supplier<BigInteger> idGenerator) {
		BrokerProperties properties = new BrokerProperties();
		// set default from env
		if (env.containsProperty("spring.application.name")) {
			properties.setId(env.getProperty("spring.application.name"));
		}
		properties.setRouteId(idGenerator.get());
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
			BrokerProperties properties, RSocketStrategies rSocketStrategies) {
		return new GatewaySocketAcceptor(rsocketFactory, filters, meterRegistry,
				properties, rSocketStrategies.metadataExtractor());
	}

	@Bean
	public GatewayServerRSocketFactoryCustomizer gatewayServerRSocketFactoryCustomizer(
			BrokerProperties properties, MeterRegistry meterRegistry) {
		return new GatewayServerRSocketFactoryCustomizer(properties, meterRegistry);
	}

	@Bean
	public RSocketServerBootstrap gatewayRSocketServerBootstrap(
			RSocketServerFactory rSocketServerFactory,
			GatewaySocketAcceptor gatewaySocketAcceptor) {
		return new RSocketServerBootstrap(rSocketServerFactory, gatewaySocketAcceptor);
	}

	@Bean
	@ConditionalOnProperty(value = "spring.cloud.gateway.rsocket.broker.actuator.enabled",
			matchIfMissing = true)
	public BrokerActuatorHandlerRegistration brokerActuatorHandlerRegistration(
			RoutingTable routingTable, RSocketMessageHandler messageHandler,
			BrokerProperties properties) {
		return new BrokerActuatorHandlerRegistration(routingTable, messageHandler,
				properties);
	}

	@Bean
	@ConditionalOnProperty(value = "spring.cloud.gateway.rsocket.broker.actuator.enabled",
			matchIfMissing = true)
	public BrokerActuator brokerActuator(BrokerProperties properties,
			ClusterService clusterService, RoutingTable routingTable) {
		return new BrokerActuator(properties, clusterService, routingTable);
	}

	@Configuration
	@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.cluster.enabled",
			matchIfMissing = true)
	protected static class ClusterConfiguration {

		@Bean
		public ClusterService clusterService() {
			return new ClusterService();
		}

		@Bean
		public ClusterJoinListener clusterJoinListener(ClusterService clusterService,
				BrokerProperties properties, RSocketStrategies strategies,
				GatewayRSocketFactory gatewayRSocketFactory) {
			return new ClusterJoinListener(clusterService, properties, strategies,
					gatewayRSocketFactory);
		}

		@Bean
		public RouteJoinListener routeJoinListener(ClusterService clusterService,
				RoutingTable routingTable, BrokerProperties properties) {
			return new RouteJoinListener(clusterService, routingTable, properties);
		}

	}

}
