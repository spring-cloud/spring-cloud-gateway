/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.EnableGateway;
import org.springframework.cloud.gateway.api.RouteDefinitionLocator;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator;
import org.springframework.cloud.gateway.support.CachingRouteDefinitionLocator;
import org.springframework.cloud.gateway.support.CompositeRouteDefinitionLocator;
import org.springframework.cloud.gateway.support.InMemoryRouteDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableGateway
public class GatewaySampleApplication {
	@Bean
	public PropertiesRouteDefinitionLocator propertiesRouteLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	@Bean
	@Primary
	public RouteDefinitionLocator compositeRouteLocator(InMemoryRouteDefinitionRepository inMemoryRouteRepository,
														PropertiesRouteDefinitionLocator propertiesRouteLocator) {
		Flux<RouteDefinitionLocator> flux = Flux.just(inMemoryRouteRepository, propertiesRouteLocator);
		CompositeRouteDefinitionLocator composite = new CompositeRouteDefinitionLocator(flux);
		return new CachingRouteDefinitionLocator(composite);
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
