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
 *
 */

package org.springframework.cloud.gateway.config;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.RestController;

@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({"spring-cloud-netflix-ribbon-*.jar"})
public class GatewayNoLoadBalancerClientAutoConfigurationTests {

	private static int port;

	@BeforeClass
	public static void init() {
		port = SocketUtils.findAvailableTcpPort();
	}

	@Test
	public void noLoadBalancerClientReportsError() {
		try (ConfigurableApplicationContext context = new SpringApplication(Config.class)
				.run("--server.port="+port, "--spring.jmx.enabled=false")) {
			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
			client.get()
					.header(HttpHeaders.HOST, "www.lbfail.org")
					.exchange()
					.expectStatus().is5xxServerError();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@RestController
	@Import(PermitAllSecurityConfiguration.class)
	public static class Config {

		@Bean
		public RouteLocator hystrixRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("lb_fail", r -> r.host("**.lbfail.org")
							.uri("lb://fail"))
					.build();
		}
	}
}
