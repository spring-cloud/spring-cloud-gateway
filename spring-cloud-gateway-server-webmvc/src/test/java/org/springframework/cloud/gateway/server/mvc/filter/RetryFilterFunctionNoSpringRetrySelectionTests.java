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

import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for retry filter selection when Spring Retry is NOT on the classpath. Verifies
 * that FrameworkRetryFilterFunctions is used automatically when Spring Retry is not
 * available.
 *
 * This test uses ClassPathExclusions to exclude Spring Retry from the classpath and
 * verifies that the selection logic correctly chooses FrameworkRetryFilterFunctions.
 *
 * NOTE: Due to classloader limitations when excluding Spring Retry
 * (GatewayRetryFilterFunctions has direct imports from org.springframework.retry.*), this
 * test verifies the selection logic by checking that Spring Retry classes are not
 * available and that filters can still be created. The actual behavior when Spring Retry
 * is not on classpath is verified by the selection logic in RetryFilterFunctions which
 * checks ClassUtils.isPresent().
 */
@ClassPathExclusions({ "spring-retry-*.jar" })
class RetryFilterFunctionNoSpringRetrySelectionTests {

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
	void springRetryNotOnClasspath() {
		// Verify that Spring Retry is actually excluded
		boolean springRetryPresent = ClassUtils.isPresent("org.springframework.retry.annotation.Retryable",
				ClassUtils.getDefaultClassLoader());
		assertThat(springRetryPresent).as("Spring Retry should not be on classpath").isFalse();
	}

	@Test
	void retryFilterUsesFrameworkRetryWhenSpringRetryNotOnClasspath() {
		// When Spring Retry is NOT on classpath, should automatically use
		// FrameworkRetryFilterFunctions
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions.retry(3);
		assertThat(filter).isNotNull();
		// The filter should be created successfully even without Spring Retry
		// Verify log message indicates FrameworkRetryFilterFunctions is selected
		assertThat(listAppender.list).hasSizeGreaterThanOrEqualTo(1);
		String logMessage = listAppender.list.get(listAppender.list.size() - 1).getMessage();
		assertThat(logMessage).contains("Retry filter selection");
		assertThat(logMessage).contains("selected filter=FrameworkRetryFilterFunctions");
		assertThat(logMessage).contains("Spring Retry on classpath=false");
	}

	@Test
	void retryFilterWithConfigUsesFrameworkRetryWhenSpringRetryNotOnClasspath() {
		RetryFilterFunctions.RetryConfig config = new RetryFilterFunctions.RetryConfig();
		config.setRetries(3);
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions.retry(config);
		assertThat(filter).isNotNull();
	}

	@Test
	void retryFilterWithConfigConsumerUsesFrameworkRetryWhenSpringRetryNotOnClasspath() {
		HandlerFilterFunction<ServerResponse, ServerResponse> filter = RetryFilterFunctions
			.retry(config -> config.setRetries(3));
		assertThat(filter).isNotNull();
	}

}
