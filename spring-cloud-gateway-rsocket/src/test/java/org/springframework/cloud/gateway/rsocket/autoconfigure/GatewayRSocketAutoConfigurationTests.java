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

import io.rsocket.SocketAcceptor;
import org.junit.Test;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.rsocket.server.RSocketServer;
import org.springframework.boot.rsocket.server.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServerFactory;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.rsocket.core.GatewayServerRSocketFactoryCustomizer;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.registry.RegistryRoutes;
import org.springframework.cloud.gateway.rsocket.registry.RegistrySocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.GatewaySocketAcceptor;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicate;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicateFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GatewayRSocketAutoConfigurationTests {

	@Test
	public void gatewayRSocketConfigured() {
		new ReactiveWebApplicationContextRunner().withUserConfiguration(MyConfig.class)
				.withConfiguration(
						AutoConfigurations.of(GatewayRSocketAutoConfiguration.class,
								CompositeMeterRegistryAutoConfiguration.class,
								MetricsAutoConfiguration.class))
				.run(context -> assertThat(context).hasSingleBean(Registry.class)
						.hasSingleBean(RegistryRoutes.class)
						.hasSingleBean(RegistrySocketAcceptorFilter.class)
						.hasSingleBean(GatewayServerRSocketFactoryCustomizer.class)
						.hasSingleBean(GatewayRSocketProperties.class)
						.hasSingleBean(GatewaySocketAcceptor.class)
						.hasSingleBean(SocketAcceptorPredicateFilter.class)
						.hasSingleBean(RSocketServerBootstrap.class)
						.doesNotHaveBean(SocketAcceptorPredicate.class));
	}

	@Configuration
	protected static class MyConfig {

		@Bean
		RSocketServerFactory rSocketServerFactory() {
			RSocketServerFactory serverFactory = mock(RSocketServerFactory.class);
			when(serverFactory.create(any(SocketAcceptor.class)))
					.thenReturn(mock(RSocketServer.class));
			return serverFactory;
		}

	}

}
