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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for retry filter selection logic.
 */
class RetryFilterFunctionSelectionLogicTests {

	private ListAppender<ILoggingEvent> listAppender;

	@BeforeEach
	void setUp() {
		Logger logger = (Logger) LoggerFactory.getLogger(RetryFilterFunctions.class);
		listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);
		logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
	}

	@AfterEach
	void tearDown() {
		if (listAppender != null) {
			Logger logger = (Logger) LoggerFactory.getLogger(RetryFilterFunctions.class);
			logger.detachAppender(listAppender);
			listAppender.stop();
		}
	}

	@AfterEach
	void reset() {
		// Reset the useFrameworkRetry flag after each test
		RetryFilterFunctions.setUseFrameworkRetry(false);
	}

	@Test
	void retryWithIntDelegatesCorrectly() {
		// Test that retry(int) delegates to the correct implementation
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions.retry(3);
		assertThat(filter).isNotNull();
		// The actual implementation depends on classpath, but it should not be null
		// Verify log message indicates filter selection
		assertThat(listAppender.list).hasSizeGreaterThanOrEqualTo(1);
		assertThat(listAppender.list.get(0).getMessage()).contains("Retry filter selection");
	}

	@Test
	void retryWithConfigConsumerDelegatesCorrectly() {
		// Test that retry(Consumer<RetryConfig>) delegates to the correct implementation
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions
			.retry(config -> config.setRetries(3));
		assertThat(filter).isNotNull();
	}

	@Test
	void retryWithConfigDelegatesCorrectly() {
		// Test that retry(RetryConfig) delegates to the correct implementation
		RetryFilterFunctions.RetryConfig config = new RetryFilterFunctions.RetryConfig();
		config.setRetries(3);
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions.retry(config);
		assertThat(filter).isNotNull();
	}

	@Test
	void forcedFrameworkRetryUsesFrameworkRetry() {
		// When forced to use Framework Retry, should use FrameworkRetryFilterFunctions
		RetryFilterFunctions.setUseFrameworkRetry(true);

		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions.retry(3);
		assertThat(filter).isNotNull();

		// Verify log message indicates FrameworkRetryFilterFunctions is selected
		assertThat(listAppender.list).hasSizeGreaterThanOrEqualTo(1);
		String logMessage = listAppender.list.get(listAppender.list.size() - 1).getMessage();
		assertThat(logMessage).contains("Retry filter selection");
		assertThat(logMessage).contains("selected filter=FrameworkRetryFilterFunctions");
		assertThat(logMessage).contains("useFrameworkRetry=true");

		// Reset for other tests
		RetryFilterFunctions.setUseFrameworkRetry(false);
	}

	@Test
	void defaultBehaviorRespectsClasspath() {
		// Default behavior should use Spring Retry if on classpath, Framework Retry
		// otherwise
		RetryFilterFunctions.setUseFrameworkRetry(false);

		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions.retry(3);
		assertThat(filter).isNotNull();
		// The actual implementation depends on whether Spring Retry is on classpath
		// In this project, Spring Retry is on classpath, so it should use
		// GatewayRetryFilterFunctions
		// Verify log message indicates correct filter selection
		assertThat(listAppender.list).hasSizeGreaterThanOrEqualTo(1);
		String logMessage = listAppender.list.get(listAppender.list.size() - 1).getMessage();
		assertThat(logMessage).contains("Retry filter selection");
		// Since Spring Retry is on classpath in this project, it should select
		// GatewayRetryFilterFunctions
		assertThat(logMessage).contains("selected filter=GatewayRetryFilterFunctions");
		assertThat(logMessage).contains("useFrameworkRetry=false");
	}

}
