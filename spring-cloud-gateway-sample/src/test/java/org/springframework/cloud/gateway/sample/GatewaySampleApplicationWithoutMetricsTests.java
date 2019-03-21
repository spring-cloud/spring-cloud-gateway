/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import java.time.Duration;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.sample.GatewaySampleApplicationTests.TestConfig;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;

@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "micrometer-*.jar", "spring-boot-actuator-*.jar", "spring-boot-actuator-autoconfigure-*.jar" })
@DirtiesContext
public class GatewaySampleApplicationWithoutMetricsTests {

	static protected int port;

	protected WebTestClient webClient;
	protected String baseUri;

	@BeforeClass
	public static void beforeClass() {
		port = SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", Integer.toString(port));
	}

	@AfterClass
	public static void afterClass() {
		System.clearProperty("server.port");
	}

	@Before
	public void setup() {
		baseUri = "http://localhost:" + port;
		this.webClient = WebTestClient.bindToServer()
				.responseTimeout(Duration.ofSeconds(10)).baseUrl(baseUri).build();
	}

	protected ConfigurableApplicationContext init(Class<?> config) {
		return new SpringApplicationBuilder().web(WebApplicationType.REACTIVE)
				.sources(GatewaySampleApplication.class, config).run();
	}

	@Test
	public void actuatorMetrics() {
		init(TestConfig.class);
		webClient.get().uri("/get").exchange().expectStatus().isOk();
		webClient.get()
				.uri("http://localhost:" + port + "/actuator/metrics/gateway.requests")
				.exchange().expectStatus().isOk().expectBody(String.class).isEqualTo(
						GatewaySampleApplication.HELLO_FROM_FAKE_ACTUATOR_METRICS_GATEWAY_REQUESTS);
	}

}
