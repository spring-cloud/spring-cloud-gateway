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
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.PropertiesRouteLocator;
import org.springframework.cloud.gateway.support.CachingRouteLocator;
import org.springframework.cloud.gateway.support.CompositeRouteLocator;
import org.springframework.cloud.gateway.support.InMemoryRouteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class GatewaySampleApplication {
	@Bean
	public PropertiesRouteLocator propertiesRouteLocator(GatewayProperties properties) {
		return new PropertiesRouteLocator(properties);
	}

	@Bean
	@Primary
	public RouteLocator compositeRouteLocator(InMemoryRouteRepository inMemoryRouteRepository,
											  PropertiesRouteLocator propertiesRouteLocator) {
		Flux<RouteLocator> flux = Flux.just(inMemoryRouteRepository, propertiesRouteLocator);
		CompositeRouteLocator composite = new CompositeRouteLocator(flux);
		return new CachingRouteLocator(composite);
	}

	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv4Stack", "true"); //Remove when configurable
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
