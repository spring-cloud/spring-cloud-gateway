/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.discovery;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class ReactiveGatewayDiscoveryClientAutoConfigurationTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.discovery.locator.enabled=true",
					"spring.cloud.gateway.loadbalancer.use404=true",
					"spring.cloud.discovery.client.simple.instances.service[0].uri=https://service1:443" })
	public static class EnabledByProperty {

		@Autowired(required = false)
		private DiscoveryClientRouteDefinitionLocator locator;

		@Autowired(required = false)
		private GatewayLoadBalancerProperties properties;

		@Test
		public void routeLocatorBeanExists() {
			assertThat(locator).as("DiscoveryClientRouteDefinitionLocator was null").isNotNull();
			List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();
			assertThat(definitions).hasSize(1);
		}

		@Test
		public void use404() {
			assertThat(properties.isUse404()).isTrue();
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class DisabledByDefault {

		@Autowired(required = false)
		private DiscoveryClientRouteDefinitionLocator locator;

		@Test
		public void routeLocatorBeanMissing() {
			assertThat(locator).as("DiscoveryClientRouteDefinitionLocator exists").isNull();
		}

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

	}

}
