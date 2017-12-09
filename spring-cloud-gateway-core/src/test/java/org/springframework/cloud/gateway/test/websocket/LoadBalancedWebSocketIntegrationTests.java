/*
 * Copyright 2002-2017 the original author or authors.
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
 */

package org.springframework.cloud.gateway.test.websocket;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Tim Ysewyn
 */
public class LoadBalancedWebSocketIntegrationTests extends AbstractWebSocketIntegrationTests {

	@Override
	protected Class getConfigClass() {
		return GatewayConfig.class;
	}

	@Configuration
	@EnableAutoConfiguration
	@RibbonClients({
			@RibbonClient(name = "myservice", configuration = TestRibbonConfig.class)
	})
	@Import(PermitAllSecurityConfiguration.class)
	protected static class GatewayConfig {

		@Bean
		public RouteLocator wsRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route(r -> r.alwaysTrue()
						.uri("ws+lb://myservice"))
					.build();
		}
	}

	protected static class TestRibbonConfig {

		@Value("${ws.server.port}")
		private int serverPort;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("localhost", this.serverPort));
		}
	}

}
