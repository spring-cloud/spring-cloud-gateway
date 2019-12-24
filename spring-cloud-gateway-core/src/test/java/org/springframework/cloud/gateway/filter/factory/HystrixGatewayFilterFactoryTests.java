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

package org.springframework.cloud.gateway.filter.factory;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.metric.consumer.HealthCountsStream;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.filter.factory.ExceptionFallbackHandler.RETRIEVED_EXCEPTION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "debug=true")
@ContextConfiguration(classes = HystrixTestConfig.class)
@DirtiesContext
public class HystrixGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void hystrixFilterWorks() {
		testClient.get().uri("/get").header("Host", "www.hystrixsuccess.org").exchange()
				.expectStatus().isOk().expectHeader()
				.valueEquals(ROUTE_ID_HEADER, "hystrix_success_test");
	}

	@Test
	public void hystrixFilterTimesout() {
		testClient.get().uri("/delay/3").header("Host", "www.hystrixfailure.org")
				.exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
				.expectBody().jsonPath("$.status")
				.isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()));
	}

	@Test
	public void hystrixFilterServiceUnavailable() {
		HealthCountsStream.reset();
		Hystrix.reset();
		ConfigurationManager.getConfigInstance()
				.setProperty("hystrix.command.failcmd.circuitBreaker.forceOpen", true);

		testClient.get().uri("/delay/3").header("Host", "www.hystrixfailure.org")
				.exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

		HealthCountsStream.reset();
		Hystrix.reset();
		ConfigurationManager.getConfigInstance()
				.setProperty("hystrix.command.failcmd.circuitBreaker.forceOpen", false);
	}

	/*
	 * Tests that timeouts bubbling from the underpinning WebClient are treated the same
	 * as Hystrix timeouts in terms of outside response. (Internally, timeouts from the
	 * WebClient are seen as command failures and trigger the opening of circuit breakers
	 * the same way timeouts do; it may be confusing in terms of the Hystrix metrics
	 * though)
	 */
	@Test
	public void hystrixTimeoutFromWebClient() {
		testClient.get().uri("/delay/10").header("Host", "www.hystrixresponsestall.org")
				.exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	public void hystrixFilterFallback() {
		testClient.get().uri("/delay/3?a=b").header("Host", "www.hystrixfallback.org")
				.exchange().expectStatus().isOk().expectBody()
				.json("{\"from\":\"fallbackcontroller\"}");
	}

	@Test
	public void hystrixFilterExceptionFallback() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.hystrixexceptionfallback.org").exchange()
				.expectStatus().isOk().expectHeader()
				.value(RETRIEVED_EXCEPTION, containsString("HystrixTimeoutException"));
	}

	@Test
	public void hystrixFilterWorksJavaDsl() {
		testClient.get().uri("/get").header("Host", "www.hystrixjava.org").exchange()
				.expectStatus().isOk().expectHeader()
				.valueEquals(ROUTE_ID_HEADER, "hystrix_java");
	}

	@Test
	public void hystrixFilterFallbackJavaDsl() {
		testClient.get().uri("/delay/3").header("Host", "www.hystrixjava.org").exchange()
				.expectStatus().isOk().expectBody()
				.json("{\"from\":\"fallbackcontroller2\"}");
	}

	@Test
	public void hystrixFilterConnectFailure() {
		testClient.get().uri("/delay/3").header("Host", "www.hystrixconnectfail.org")
				.exchange().expectStatus().is5xxServerError();
	}

	@Test
	public void hystrixFilterErrorPage() {
		testClient.get().uri("/delay/3").header("Host", "www.hystrixconnectfail.org")
				.accept(APPLICATION_JSON).exchange().expectStatus().is5xxServerError()
				.expectBody().jsonPath("$.status")
				.value(Matchers.greaterThanOrEqualTo(500)).jsonPath("$.message")
				.isNotEmpty().jsonPath("$.error").isNotEmpty();
	}

	@Test
	public void toStringFormat() {
		HystrixGatewayFilterFactory.Config config = new HystrixGatewayFilterFactory.Config()
				.setName("myname").setFallbackUri("forward:/myfallback");
		GatewayFilter filter = new HystrixGatewayFilterFactory(null).apply(config);
		assertThat(filter.toString()).contains("myname").contains("forward:/myfallback");
	}

	@Test
	public void filterFallbackForward() {
		testClient.get().uri("/delay/3?a=c").header("Host", "www.hystrixforward.org")
				.exchange().expectStatus().isOk().expectBody()
				.json("{\"from\":\"hystrixfallbackcontroller3\"}");
	}

}
