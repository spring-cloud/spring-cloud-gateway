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

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory.NameConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RemoveRequestParameterGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	@Test
	public void removeResponseHeaderFilterWorks() {
		testClient.get().uri("/get?foo=bar&baz=bam%20bar").header("Host", "www.removerequestparamjava.org").exchange()
				.expectStatus().isOk().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> params = getMap(result.getResponseBody(), "args");
					assertThat(params).doesNotContainKey("foo");
					assertThat(params).containsEntry("baz", "bam%20bar");
				});
	}

	@Test
	public void toStringFormat() {
		NameConfig config = new NameConfig();
		config.setName("myname");
		GatewayFilter filter = new RemoveRequestParameterGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname");
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
					.route("removerequestparam_java_test",
							r -> r.path("/get").and().host("**.removerequestparamjava.org")
									.filters(f -> f.prefixPath("/httpbin").removeRequestParameter("foo")).uri(uri))
					.build();
		}

	}

}
