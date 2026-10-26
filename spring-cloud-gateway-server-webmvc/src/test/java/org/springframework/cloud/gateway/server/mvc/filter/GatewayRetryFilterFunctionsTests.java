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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link GatewayRetryFilterFunctions}.
 *
 * @author Seungtaek Lim
 */
@SuppressWarnings("deprecation")
class GatewayRetryFilterFunctionsTests {

	@Test
	void retriesConfiguredException() throws Exception {
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = GatewayRetryFilterFunctions
			.retry(config -> config.setRetries(3).addExceptions(IllegalStateException.class));
		AtomicInteger attempts = new AtomicInteger();

		ServerResponse response = filter.filter(request(), request -> {
			if (attempts.incrementAndGet() <= 2) {
				throw new IllegalStateException("temporarily broken");
			}
			return ServerResponse.ok().build();
		});

		assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
		assertThat(attempts.get()).isEqualTo(3);
	}

	@Test
	void retriesConfiguredNumberOfTimes() throws Exception {
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = GatewayRetryFilterFunctions
			.retry(config -> config.setRetries(3));
		AtomicInteger attempts = new AtomicInteger();

		ServerResponse response = filter.filter(request(), request -> {
			if (attempts.incrementAndGet() <= 3) {
				return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
			return ServerResponse.ok().build();
		});

		assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
		assertThat(attempts.get()).isEqualTo(4);
	}

	@Test
	void doesNotRetryUnconfiguredException() {
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = GatewayRetryFilterFunctions
			.retry(config -> config.setRetries(3).addExceptions(IllegalStateException.class));
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> filter.filter(request(), request -> {
			attempts.incrementAndGet();
			throw new IllegalArgumentException("not retryable");
		})).isInstanceOf(IllegalArgumentException.class);
		assertThat(attempts.get()).isEqualTo(1);
	}

	@Test
	void stopsRetryingWhenRetriesAreExhausted() {
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = GatewayRetryFilterFunctions
			.retry(config -> config.setRetries(3).addExceptions(IllegalStateException.class));
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> filter.filter(request(), request -> {
			attempts.incrementAndGet();
			throw new IllegalStateException("always broken");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(attempts.get()).isEqualTo(4);
	}

	private ServerRequest request() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/retry");
		return ServerRequest.create(servletRequest, Collections.emptyList());
	}

}
