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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class HystrixGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void hystrixFilterWorks() {
		testClient.get().uri("/get")
				.header("Host", "www.hystrixsuccess.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "hystrix_success_test");
	}

	@Test
	public void hystrixFilterTimesout() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.hystrixfailure.org")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	public void hystrixFilterFallback() {
		testClient.get().uri("/delay/3?a=b")
				.header("Host", "www.hystrixfallback.org")
				.exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"from\":\"fallbackcontroller\"}");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RestController
	public static class TestConfig {
		@RequestMapping("/fallbackcontroller")
		public Map<String, String> fallbackcontroller(@RequestParam("a") String a) {
			return Collections.singletonMap("from", "fallbackcontroller");
		}
	}

}
