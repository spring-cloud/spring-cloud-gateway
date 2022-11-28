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

package org.springframework.cloud.gateway.support.tagsprovider;

import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ingyu Hwang
 */
public class PropertiesTagsProviderTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void test() {
		contextRunner.withConfiguration(AutoConfigurations.of(GatewayMetricsAutoConfiguration.class))
				.withPropertyValues("spring.cloud.gateway.metrics.tags.foo1=bar1",
						"spring.cloud.gateway.metrics.tags.foo2=bar2")
				.run(context -> {
					PropertiesTagsProvider provider = context.getBean(PropertiesTagsProvider.class);
					Tags tags = provider.apply(MockServerWebExchange.from(MockServerHttpRequest.get("").build()));
					assertThat(tags).isEqualTo(Tags.of("foo1", "bar1", "foo2", "bar2"));
				});

	}

}
