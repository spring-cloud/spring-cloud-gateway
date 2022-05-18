/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@RestController
@LoadBalancerClient(name = "myservice", configuration = MyServiceConf.class)
public class MvcFailureAnalyzerApplication {

	@GetMapping("hello")
	public String hello() {
		return "Hello";
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
	public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes().route(r -> r.path("/myprefix/**").filters(f -> f.stripPrefix(1)).uri("lb://myservice"))
				.build();
	}

}

class MyServiceConf {

	@Value("${local.server.port}")
	private int port = 0;

	@Bean
	public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
		return ServiceInstanceListSuppliers.from("myservice",
				new DefaultServiceInstance("myservice-1", "myservice", "localhost", port, false));
	}

}
