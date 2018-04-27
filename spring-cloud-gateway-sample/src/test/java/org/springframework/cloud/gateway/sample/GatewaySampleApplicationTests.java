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

import java.time.Duration;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.test.HttpBinCompatibleController;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { GatewaySampleApplicationTests.TestConfig.class},
		webEnvironment = RANDOM_PORT)
public class GatewaySampleApplicationTests {

	@LocalServerPort
	protected int port = 0;

	protected static int managementPort;

	protected WebTestClient webClient;
	protected String baseUri;

	@BeforeClass
	public static void beforeClass() {
		managementPort = SocketUtils.findAvailableTcpPort();

		System.setProperty("management.server.port", String.valueOf(managementPort));
	}

	@AfterClass
	public static void afterClass() {
		System.clearProperty("management.server.port");
	}

	@Before
	public void setup() {
		baseUri = "http://localhost:" + port;
		this.webClient = WebTestClient.bindToServer().responseTimeout(Duration.ofSeconds(10)).baseUrl(baseUri).build();
	}

	@Test
	public void contextLoads() {
		webClient.get()
				.uri("/get")
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	public void complexPredicate() {
		webClient.get()
				.uri("/anything/png")
				.header("Host", "www.abc.org")
				.exchange()
				.expectHeader().valueEquals("X-TestHeader", "foobar")
				.expectStatus().isOk();

	}

	@Test
	public void actuatorManagementPort() {
		webClient.get()
				.uri("http://localhost:"+managementPort+"/actuator/gateway/routes")
				.exchange()
				.expectStatus().isOk();
	}

	@Configuration
	@EnableAutoConfiguration
	@RibbonClient(name = "httpbin", configuration = RibbonConfig.class)
	@Import(GatewaySampleApplication.class)
	protected static class TestConfig {
		@Bean
		public HttpBinCompatibleController httpBinCompatibleController() {
			return new HttpBinCompatibleController();
		}

	}

	protected static class RibbonConfig {
		@LocalServerPort
		int port;

		@Bean
		@Primary
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("localhost", port));
		}
	}

}
