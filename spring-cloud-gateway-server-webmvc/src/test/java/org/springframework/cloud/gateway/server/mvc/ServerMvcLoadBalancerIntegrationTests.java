/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.filter.FilterAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Integration tests for {@link FilterAutoConfiguration.LoadBalancerHandlerConfiguration}.
 *
 * @author Olga Maciaszek-Sharma
 *
 */
@SpringBootTest(classes = { ServerMvcLoadBalancerIntegrationTests.Config.class, FilterAutoConfiguration.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
@ActiveProfiles("lb")
public class ServerMvcLoadBalancerIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	RestTestClient restClient;

	@Test
	void shouldUseLbHandlerFunctionDefinitionToResolveHost() {
		restClient.get().uri("http://localhost:" + port + "/test").exchange().expectStatus().isOk();
	}

	@SpringBootApplication
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	static class Config {

	}

}
