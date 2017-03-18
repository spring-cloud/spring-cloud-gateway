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

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.EnableGateway;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.context.annotation.Bean;
import org.springframework.tuple.TupleBuilder;
import org.springframework.web.reactive.function.server.RequestPredicates;

import static org.springframework.cloud.gateway.support.RouteDefinitionRouteLocator.loadFilters;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableGateway
public class GatewaySampleApplication {

	@Bean
	public RouteLocator customRouteLocator(List<GlobalFilter> globalFilters) {
		return () -> {
			Route route = Route.builder()
					.id("test")
					.uri("http://httpbin.org:80")
					.requestPredicate(RequestPredicates.path("/image/png"))
					.addAll(loadFilters(globalFilters))
					.add(new AddResponseHeaderWebFilterFactory()
							.apply(TupleBuilder.tuple().of("name", "X-TestHeader", "value", "foobar")))
					.build();
			return Flux.just(route);
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
