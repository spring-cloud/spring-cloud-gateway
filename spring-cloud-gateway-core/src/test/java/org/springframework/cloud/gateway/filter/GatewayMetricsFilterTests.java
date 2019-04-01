/*
 * Copyright 2013-2018 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.MeterRegistry;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class GatewayMetricsFilterTests extends BaseWebClientTests {

	private static final String REQUEST_METRICS_NAME = "gateway.requests";

	@Autowired
	private MeterRegistry meterRegistry;

	@Value("${test.uri}")
	private String testUri;

	@Test
	public void gatewayRequestsMeterFilterHasTags() throws InterruptedException {
		testClient.get().uri("/headers").exchange().expectStatus().isOk();
		assertMetricsContainsTag("outcome", HttpStatus.Series.SUCCESSFUL.name());
		assertMetricsContainsTag("status", HttpStatus.OK.name());
		assertMetricsContainsTag("httpStatusCode", String.valueOf(HttpStatus.OK.value()));
		assertMetricsContainsTag("httpMethod", HttpMethod.GET.toString());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", testUri);
	}

	@Test
	public void gatewayRequestsMeterFilterHasTagsForBadTargetUri()
			throws InterruptedException {
		testClient.get().uri("/badtargeturi").exchange().expectStatus()
				.is5xxServerError();
		assertMetricsContainsTag("outcome", HttpStatus.Series.SERVER_ERROR.name());
		assertMetricsContainsTag("status", HttpStatus.INTERNAL_SERVER_ERROR.name());
		assertMetricsContainsTag("httpStatusCode", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
		assertMetricsContainsTag("httpMethod", HttpMethod.GET.toString());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", testUri);
	}

	@Test
	public void hasMetricsForSetStatusFilter() throws InterruptedException {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.HOST, "www.setcustomstatus.org");
		// cannot use netty client since we cannot read custom http status
		ResponseEntity<String> response = new TestRestTemplate().exchange(
				baseUri + "/headers", HttpMethod.POST, new HttpEntity<>(headers),
				String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(432);
		assertMetricsContainsTag("outcome", "CUSTOM");
		assertMetricsContainsTag("status", "432");
		assertMetricsContainsTag("routeId", "test_custom_http_status");
		assertMetricsContainsTag("routeUri", testUri);
		assertMetricsContainsTag("httpStatusCode", "432");
		assertMetricsContainsTag("httpMethod", HttpMethod.POST.toString());
	}

	private void assertMetricsContainsTag(String tagKey, String tagValue) {
		assertThat(this.meterRegistry.get(REQUEST_METRICS_NAME).tag(tagKey, tagValue)
				.timer().count()).isEqualTo(1);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@RestController
	@Import(DefaultTestConfig.class)
	public static class CustomConfig {
		@Value("${test.uri}")
		protected String testUri;

		@Bean
		public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test_custom_http_status", r -> r.host("*.setcustomstatus.org")
							.filters(f -> f.setStatus(432)).uri(testUri))
					.build();
		}

		@RequestMapping("/httpbin/badtargeturi")
		public String exception() {
			throw new RuntimeException("an error");
		}
	}
}
