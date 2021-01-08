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

package org.springframework.cloud.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.config.GatewayMetricsProperties.DEFAULT_PREFIX;

/**
 * @author Ingyu Hwang
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class GatewayMetricsFilterCustomTagsTests extends BaseWebClientTests {

	private static final String REQUEST_METRICS_NAME = DEFAULT_PREFIX + ".requests";

	@Autowired
	private MeterRegistry meterRegistry;

	@Value("${test.uri}")
	private String testUri;

	@Test
	public void gatewayRequestsMeterFilterHasCustomTags() {
		testClient.get().uri("/headers").exchange().expectStatus().isOk();

		// default tags
		assertMetricsContainsTag("outcome", HttpStatus.Series.SUCCESSFUL.name());
		assertMetricsContainsTag("status", HttpStatus.OK.name());
		assertMetricsContainsTag("httpStatusCode", String.valueOf(HttpStatus.OK.value()));
		assertMetricsContainsTag("httpMethod", HttpMethod.GET.toString());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", testUri);

		// custom tags
		assertMetricsContainsTag("custom1", "tag1");
		assertMetricsContainsTag("custom2", "tag2");
	}

	private void assertMetricsContainsTag(String tagKey, String tagValue) {
		assertThat(this.meterRegistry.get(REQUEST_METRICS_NAME).tag(tagKey, tagValue).timer().count()).isEqualTo(1);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class CustomConfig {

		@Bean
		public GatewayTagsProvider customGatewayTagsProvider() {
			return exchange -> Tags.of("custom1", "tag1", "custom2", "tag2");
		}

	}

}
