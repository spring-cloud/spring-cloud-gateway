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

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RetryFilterFunctionBackoffTests {

	private static final Duration BACKOFF = Duration.ofMillis(50);

	private static final Duration MAX_BACKOFF = Duration.ofMillis(100);

	@Test
	@SuppressWarnings("deprecation")
	void gatewayRetryUsesConfiguredBackoff() throws Exception {
		assertBackoff(GatewayRetryFilterFunctions
			.retry(config -> config.setRetries(2).setBackoff(BACKOFF, MAX_BACKOFF, 2, false)));
	}

	@Test
	void frameworkRetryUsesConfiguredBackoff() throws Exception {
		assertBackoff(FrameworkRetryFilterFunctions
			.frameworkRetry(config -> config.setRetries(2).setBackoff(BACKOFF, MAX_BACKOFF, 2, false)));
	}

	@Test
	void retryConfigBindsBackoff() {
		MapConfigurationPropertySource source = new MapConfigurationPropertySource(
				Map.of("backoff.firstBackoff", "100ms", "backoff.maxBackoff", "500ms", "backoff.factor", "2",
						"backoff.basedOnPreviousValue", "false"));

		RetryFilterFunctions.RetryConfig config = new Binder(Collections.singletonList(source), null,
				ApplicationConversionService.getSharedInstance())
			.bindOrCreate("", Bindable.of(RetryFilterFunctions.RetryConfig.class));

		assertThat(config.getBackoff()).isNotNull();
		assertThat(config.getBackoff().getFirstBackoff()).isEqualTo(Duration.ofMillis(100));
		assertThat(config.getBackoff().getMaxBackoff()).isEqualTo(Duration.ofMillis(500));
		assertThat(config.getBackoff().getFactor()).isEqualTo(2);
		assertThat(config.getBackoff().isBasedOnPreviousValue()).isFalse();
	}

	private void assertBackoff(HandlerFilterFunction<ServerResponse, ServerResponse> filter) throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		long start = System.nanoTime();

		ServerResponse response = filter.filter(request(), request -> {
			if (attempts.incrementAndGet() == 1) {
				return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
			return ServerResponse.ok().build();
		});

		assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
		assertThat(attempts.get()).isEqualTo(2);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(40));
	}

	private ServerRequest request() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/retry");
		return ServerRequest.create(servletRequest, Collections.emptyList());
	}

}
