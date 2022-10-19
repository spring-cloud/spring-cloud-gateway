/*
 * Copyright 2013-2020 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.http.HttpStatus;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * @author Ryan Baxter
 */
public abstract class SpringCloudCircuitBreakerFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void cbFilterWorks() {
		testClient.get().uri("/get").header("Host", "www.sccbsuccess.org").exchange().expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "sccb_success_test");
	}

	@Test
	public void cbFilterTimesout() {
		testClient.get().uri("/delay/3").header("Host", "www.sccbtimeout.org").exchange().expectStatus()
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT).expectBody().jsonPath("$.status")
				.isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()));
	}

	/*
	 * Tests that timeouts bubbling from the underpinning WebClient are treated the same
	 * as CircuitBreaker timeouts in terms of outside response. (Internally, timeouts from
	 * the WebClient are seen as command failures and trigger the opening of circuit
	 * breakers the same way timeouts do; it may be confusing in terms of the
	 * CircuitBreaker metrics though)
	 */
	@Test
	public void timeoutFromWebClient() {
		testClient.get().uri("/delay/10").header("Host", "www.circuitbreakerresponsestall.org").exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	public void filterFallback() {
		testClient.get().uri("/delay/3?a=b").header("Host", "www.circuitbreakerfallback.org").exchange().expectStatus()
				.isOk().expectBody().json("{\"from\":\"circuitbreakerfallbackcontroller\"}");
	}

	@Test
	public void filterWithVariables() {
		testClient.get().uri("/delay/3/extra?a=b").header("Host", "www.circuitbreakervariables.org").exchange()
				.expectStatus().isOk().expectBody()
				.json("{\"uri\":\"/circuitbreakerUriFallbackController/3/extra/www?a=b\"}");
	}

	@Test
	public void filterWorksJavaDsl() {
		testClient.get().uri("/get").header("Host", "www.circuitbreakerjava.org").exchange().expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "circuitbreaker_java");
	}

	@Test
	public void filterFallbackJavaDsl() {
		testClient.get().uri("/delay/3").header("Host", "www.circuitbreakerjava.org").exchange().expectStatus().isOk()
				.expectBody().json("{\"from\":\"circuitbreakerfallbackcontroller2\"}");
	}

	@Test
	public void filterConnectFailure() {
		testClient.get().uri("/delay/3").header("Host", "www.circuitbreakerconnectfail.org").exchange().expectStatus()
				.is5xxServerError();
	}

	@Test
	public void filterErrorPage() {
		testClient.get().uri("/delay/3").header("Host", "www.circuitbreakerconnectfail.org").accept(APPLICATION_JSON)
				.exchange().expectStatus().is5xxServerError().expectBody().jsonPath("$.status").isEqualTo(500)
				.jsonPath("$.message").isNotEmpty().jsonPath("$.error").isEqualTo("Internal Server Error");
	}

	@Test
	public void filterFallbackForward() {
		testClient.get().uri("/delay/3?a=c").header("Host", "www.circuitbreakerforward.org").exchange().expectStatus()
				.isOk().expectBody().json("{\"from\":\"circuitbreakerfallbackcontroller3\"}");
	}

	@Test
	public void filterStatusCodeFallback() {
		testClient.get().uri("/status/500").header("Host", "www.circuitbreakerstatuscode.org").exchange().expectStatus()
				.isOk().expectBody().json("{\"from\":\"statusCodeFallbackController\"}");

		testClient.get().uri("/status/404").header("Host", "www.circuitbreakerstatuscode.org").exchange().expectStatus()
				.isOk().expectBody().json("{\"from\":\"statusCodeFallbackController\"}");

		testClient.get().uri("/status/200").header("Host", "www.circuitbreakerstatuscode.org").exchange().expectStatus()
				.isOk().expectHeader().valueEquals(ROUTE_ID_HEADER, "circuitbreaker_fallback_test_statuscode");
	}

	@Test
	public void filterStatusCodeResumeWithoutError() {
		testClient.get().uri("/status/500").header("Host", "www.circuitbreakerresumewithouterror.org").exchange()
				.expectStatus().isEqualTo(500);

		testClient.get().uri("/status/404").header("Host", "www.circuitbreakerresumewithouterror.org").exchange()
				.expectStatus().isEqualTo(404);

		testClient.get().uri("/status/200").header("Host", "www.circuitbreakerresumewithouterror.org").exchange()
				.expectStatus().isOk().expectHeader()
				.valueEquals(ROUTE_ID_HEADER, "circuitbreaker_resume_without_error");
	}

}
