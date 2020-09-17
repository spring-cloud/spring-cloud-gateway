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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderSizeGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Sakalya Deshpande
 */

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RequestHeaderSizeGatewayFilterFactoryTest extends BaseWebClientTests {

	private static final String responseMesssage = "Request Header/s size is larger than "
			+ "permissible limit. Request Header/s size is \\d*B where permissible limit is \\d*B";

	@Test
	public void setRequestSizeFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.test.org")
				.header("HeaderName", "Some Very Large Header Name").exchange().expectStatus()
				.isEqualTo(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE).expectHeader()
				.valueMatches("errorMessage", responseMesssage);
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setMaxSize(DataSize.ofBytes(1000L));
		GatewayFilter filter = new RequestHeaderSizeGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("max").contains("1000B");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test_request_header_size",
							r -> r.order(-1).host("**.test.org")
									.filters(f -> f.setRequestHeaderSize(DataSize.of(46L, DataUnit.BYTES))).uri(uri))
					.build();
		}

	}

}
