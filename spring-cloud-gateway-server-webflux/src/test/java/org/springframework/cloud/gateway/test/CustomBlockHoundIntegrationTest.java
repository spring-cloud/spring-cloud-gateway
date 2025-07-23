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

package org.springframework.cloud.gateway.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Tim Ysewyn
 */
public class CustomBlockHoundIntegrationTest {

	@Test
	@DisabledForJreRange(min = JRE.JAVA_18)
	// Disable this test for now flaky on GitHub Actions
	@Disabled
	public void shouldThrowErrorForBlockingCallWithCustomBlockHoundIntegration() {
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> Mono.fromCallable(() -> {
			Thread.sleep(1);
			return null;
		}).subscribeOn(Schedulers.parallel()).block());
	}

}
