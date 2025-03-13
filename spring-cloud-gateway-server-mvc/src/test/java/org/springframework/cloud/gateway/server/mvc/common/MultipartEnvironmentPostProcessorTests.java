/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.common;

import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.common.MultipartEnvironmentPostProcessor.MULTIPART_ENABLED_PROPERTY;
import static org.springframework.cloud.gateway.server.mvc.common.MultipartEnvironmentPostProcessor.MULTIPART_PROPERTY_SOURCE_NAME;

public class MultipartEnvironmentPostProcessorTests {

	@Test
	void multipartDisabledByDefault() {
		MockEnvironment environment = new MockEnvironment();
		MultipartEnvironmentPostProcessor processor = new MultipartEnvironmentPostProcessor();
		processor.postProcessEnvironment(environment, null);

		assertThat(environment.getPropertySources().contains(MULTIPART_PROPERTY_SOURCE_NAME)).isTrue();

		Boolean multipartEnabled = environment.getProperty(MULTIPART_ENABLED_PROPERTY, Boolean.class);
		assertThat(multipartEnabled).isFalse();
	}

	@Test
	void multipartEnabledByUser() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(MULTIPART_ENABLED_PROPERTY, "true");
		MultipartEnvironmentPostProcessor processor = new MultipartEnvironmentPostProcessor();
		processor.postProcessEnvironment(environment, null);

		assertThat(environment.getPropertySources().contains(MULTIPART_PROPERTY_SOURCE_NAME)).isFalse();

		Boolean multipartEnabled = environment.getProperty(MULTIPART_ENABLED_PROPERTY, Boolean.class);
		assertThat(multipartEnabled).isTrue();
	}

}
