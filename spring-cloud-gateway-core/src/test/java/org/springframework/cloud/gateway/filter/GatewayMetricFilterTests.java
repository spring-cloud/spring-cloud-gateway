/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import io.micrometer.core.instrument.MeterRegistry;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class GatewayMetricFilterTests extends BaseWebClientTests {

	private static final String REQUEST_METRICS_NAME = "gateway.requests";
	
	@Autowired
	private GatewayProperties properties;

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	public void gatewayRequestsMeterFilterHasTags() {
		assertThat(this.properties.getDefaultFilters()).isNotEmpty();

		testClient.get().uri("/headers").exchange().expectStatus().isOk();
		assertMetricsContainsTag("success", Boolean.TRUE.toString());
		assertMetricsContainsTag("httpStatus", HttpStatus.OK.name());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", "lb://testservice");
	}

	private void assertMetricsContainsTag(String tagKey, String tagValue) {
		assertThat(this.meterRegistry.get(REQUEST_METRICS_NAME).tag(tagKey, tagValue).timer()
				.count()).isEqualTo(1);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
