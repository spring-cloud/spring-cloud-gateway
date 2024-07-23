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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Spencer Gibb
 */
// curl -i --insecure https://localhost:8443/hello
@SpringBootConfiguration
@EnableAutoConfiguration
@RestController
@LoadBalancerClients({ @LoadBalancerClient(name = "myservice", configuration = Http2Application.MyServiceConf.class),
		@LoadBalancerClient(name = "nossl", configuration = Http2Application.NosslServiceConf.class) })
public class Http2Application {

	private static Log log = LogFactory.getLog(Http2Application.class);

	@GetMapping("hello")
	public String hello() {
		return "Hello";
	}

	@Bean
	public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
			.route(r -> r.path("/myprefix/**").filters(f -> f.stripPrefix(1)).uri("lb://myservice"))
			.route(r -> r.path("/nossl/**").filters(f -> f.stripPrefix(1)).uri("lb://nossl"))
			.route(r -> r.path("/neverssl/**").filters(f -> f.stripPrefix(1)).uri("http://neverssl.com"))
			.route(r -> r.path("/httpbin/**").uri("https://nghttp2.org"))
			.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(Http2Application.class, args);
	}

	static class MyServiceConf {

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier(Environment env) {
			Integer port = env.getProperty("local.server.port", Integer.class, 8443);
			log.info("local.server.port = " + port);
			return ServiceInstanceListSuppliers.from("myservice",
					new DefaultServiceInstance("myservice-1", "myservice", "localhost", port, true));
		}

	}

	static class NosslServiceConf {

		@Bean
		public ServiceInstanceListSupplier noSslStaticServiceInstanceListSupplier() {
			int port = Integer.parseInt(System.getProperty("nossl.port", "8080"));
			log.info("nossl.port = " + port);
			return ServiceInstanceListSuppliers.from("nossl",
					new DefaultServiceInstance("nossl-1", "nossl", "localhost", port, false));
		}

	}

}
