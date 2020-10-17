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

package org.springframework.cloud.gateway.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
public class ModuleDeprecatedWarningAutoConfigurationTests {

	@Test
	public void warningLogged(CapturedOutput output) {
		new ReactiveWebApplicationContextRunner().withUserConfiguration(TestConfig.class)
				.run(context -> {
					assertThat(context).hasSingleBean(
							ModuleDeprecatedWarningAutoConfiguration.class);
					assertThat(output).contains(
							"spring-cloud-gateway-core module had been deprecated");
				});
	}

	@EnableAutoConfiguration
	protected static class TestConfig {

	}

}
