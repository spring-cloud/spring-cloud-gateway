/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.gateway.tests.http2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Spencer Gibb
 */
// curl -i --insecure https://localhost:8443/hello
@SpringBootConfiguration
@EnableAutoConfiguration
@RestController
// @LoadBalancerClient(name = "myservice", configuration =
// Http2Application.MyServiceConf.class)
public class Http2Application {

	@GetMapping("hello")
	public String hello() {
		return "Hello";
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
	public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes().route(r -> r.path("/myprefix/**").filters(f -> f.stripPrefix(1)).uri("lb://myservice"))
				.route(r -> r.path("/httpbin/**").uri("https://nghttp2.org")).build();
	}

	public static void main(String[] args) {
		SpringApplication.run(Http2Application.class, args);
	}

	static class MyServiceConf {

		// @LocalServerPort
		private int port = 8443;

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("myservice",
					new DefaultServiceInstance("myservice-1", "myservice", "localhost", port, true));
		}

	}

}
