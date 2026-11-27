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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.RetryConfig;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RetryFilterFunctionStatusesTests {

	@Test
	void statusesRetryOnlyConfiguredStatus() throws Exception {
		RetryConfig config = new RetryConfig().setStatuses(Set.of(HttpStatus.TOO_MANY_REQUESTS)).setSeries(Set.of());

		assertBothRetryFilters(config, HttpStatus.TOO_MANY_REQUESTS, 2);
		assertBothRetryFilters(config, HttpStatus.INTERNAL_SERVER_ERROR, 1);
	}

	@Test
	void statusesAndSeriesBothRetry() throws Exception {
		RetryConfig config = new RetryConfig().addStatuses(HttpStatus.TOO_MANY_REQUESTS)
			.setSeries(Set.of(HttpStatus.Series.SERVER_ERROR));

		assertBothRetryFilters(config, HttpStatus.TOO_MANY_REQUESTS, 2);
		assertBothRetryFilters(config, HttpStatus.INTERNAL_SERVER_ERROR, 2);
	}

	@SuppressWarnings("deprecation")
	private void assertBothRetryFilters(RetryConfig config, HttpStatus status, int expectedAttempts) throws Exception {
		assertRetries(GatewayRetryFilterFunctions.retry(config), status, expectedAttempts);
		assertRetries(FrameworkRetryFilterFunctions.frameworkRetry(config), status, expectedAttempts);
	}

	private void assertRetries(HandlerFilterFunction<ServerResponse, ServerResponse> filter, HttpStatus status,
			int expectedAttempts) throws Exception {
		ServerRequest request = ServerRequest.create(new MockHttpServletRequest("GET", "/retry"),
				Collections.emptyList());
		AtomicInteger attempts = new AtomicInteger();
		ServerResponse response = filter.filter(request, next -> attempts.incrementAndGet() == 1
				? ServerResponse.status(status).build() : ServerResponse.ok().build());

		assertThat(attempts.get()).isEqualTo(expectedAttempts);
		assertThat(response.statusCode()).isEqualTo(expectedAttempts == 1 ? status : HttpStatus.OK);
	}

}
