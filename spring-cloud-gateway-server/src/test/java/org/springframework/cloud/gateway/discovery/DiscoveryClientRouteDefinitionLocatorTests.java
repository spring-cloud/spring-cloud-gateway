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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;

@SpringBootTest(classes = DiscoveryClientRouteDefinitionLocatorTests.Config.class,
		properties = { "spring.cloud.gateway.discovery.locator.enabled=true",
				"spring.cloud.gateway.discovery.locator.route-id-prefix=testedge_",
				"spring.cloud.gateway.discovery.locator.include-expression=metadata['edge'] == 'true'",
				"spring.cloud.gateway.discovery.locator.lower-case-service-id=true"
		/*
		 * "spring.cloud.gateway.discovery.locator.predicates[0].name=Path",
		 * "spring.cloud.gateway.discovery.locator.predicates[0].args[pattern]='/'+serviceId.toLowerCase()+'/**'",
		 * "spring.cloud.gateway.discovery.locator.filters[0].name=RewritePath",
		 * "spring.cloud.gateway.discovery.locator.filters[0].args[regexp]='/' + serviceId.toLowerCase() + '/(?<remaining>.*)'"
		 * ,
		 * "spring.cloud.gateway.discovery.locator.filters[0].args[replacement]='/$\\\\{remaining}'",
		 */
		})
public class DiscoveryClientRouteDefinitionLocatorTests {

	@Autowired(required = false)
	private DiscoveryClientRouteDefinitionLocator locator;

	@Test
	public void includeExpressionWorks() {
		assertThat(locator).as("DiscoveryClientRouteDefinitionLocator was null").isNotNull();

		List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();
		assertThat(definitions).hasSize(2);

		RouteDefinition definition = definitions.get(0);
		assertThat(definition.getId()).isEqualTo("testedge_SERVICE1");
		assertThat(definition.getUri()).hasScheme("lb").hasHost("SERVICE1");
		assertThat(definition.getMetadata()).containsEntry("edge", "true");

		assertThat(definition.getPredicates()).hasSize(1);
		PredicateDefinition predicate = definition.getPredicates().get(0);
		assertThat(predicate.getName()).isEqualTo("Path");
		assertThat(predicate.getArgs()).hasSize(1).containsEntry(PATTERN_KEY, "/service1/**");

		assertThat(definition.getFilters()).hasSize(1);
		FilterDefinition filter = definition.getFilters().get(0);
		assertThat(filter.getName()).isEqualTo("RewritePath");
		assertThat(filter.getArgs()).hasSize(2).containsEntry(REGEXP_KEY, "/service1/?(?<remaining>.*)")
				.containsEntry(REPLACEMENT_KEY, "/${remaining}");

		RouteDefinition definition2 = definitions.get(1);
		assertThat(definition2.getId()).isEqualTo("testedge_service3");
		assertThat(definition2.getUri()).hasScheme("lb").hasHost("service3");
		assertThat(definition2.getMetadata()).containsEntry("edge", "true");

		assertThat(definition2.getPredicates()).hasSize(1);
		PredicateDefinition predicate2 = definition2.getPredicates().get(0);
		assertThat(predicate2.getName()).isEqualTo("Path");
		assertThat(predicate2.getArgs()).hasSize(1).containsEntry(PATTERN_KEY, "/service3/**");

		assertThat(definition.getFilters()).hasSize(1);
		FilterDefinition filter2 = definition2.getFilters().get(0);
		assertThat(filter2.getName()).isEqualTo("RewritePath");
		assertThat(filter2.getArgs()).hasSize(2).containsEntry(REGEXP_KEY, "/service3/?(?<remaining>.*)")
				.containsEntry(REPLACEMENT_KEY, "/${remaining}");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

		@Bean
		ReactiveDiscoveryClient discoveryClient() {
			ReactiveDiscoveryClient discoveryClient = mock(ReactiveDiscoveryClient.class);
			when(discoveryClient.getServices()).thenReturn(Flux.just("SERVICE1", "Service2", "service3"));
			whenInstance(discoveryClient, "SERVICE1", Collections.singletonMap("edge", "true"));
			whenInstance(discoveryClient, "Service2", Collections.emptyMap());
			whenInstance(discoveryClient, "service3", Collections.singletonMap("edge", "true"));
			return discoveryClient;
		}

		private void whenInstance(ReactiveDiscoveryClient discoveryClient, String serviceId,
				Map<String, String> metadata) {
			if ("SERVICE1".equals(serviceId) || "Service2".equals(serviceId)) {
				DefaultServiceInstance instance1 = new DefaultServiceInstance(serviceId + "8001", serviceId,
						"localhost", 8001, false, metadata);
				when(discoveryClient.getInstances(serviceId)).thenReturn(Flux.just(instance1));
			}
			else if ("service3".equalsIgnoreCase(serviceId)) {
				// given 3 instances, only one routedefinition should be created
				DefaultServiceInstance instance2 = new DefaultServiceInstance(serviceId + "8002", serviceId,
						"localhost", 8002, false, Collections.emptyMap());
				DefaultServiceInstance instance3 = new DefaultServiceInstance(serviceId + "8003", serviceId,
						"localhost", 8003, false, metadata);
				DefaultServiceInstance instance4 = new DefaultServiceInstance(serviceId + "8004", serviceId,
						"localhost", 8004, false, metadata);
				when(discoveryClient.getInstances(serviceId)).thenReturn(Flux.just(instance2, instance3, instance4));
			}
		}

	}

}
