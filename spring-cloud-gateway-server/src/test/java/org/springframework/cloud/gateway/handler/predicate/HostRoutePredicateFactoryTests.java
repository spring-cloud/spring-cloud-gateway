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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.function.Predicate;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class HostRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void hostRouteWorks() {
		expectHostRoute("www.example.org", "host_example_to_httpbin");
	}

	public void expectHostRoute(String host, String routeId) {
		testClient.get().uri("/get").header("Host", host).exchange().expectStatus().isOk().expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER, RoutePredicateHandlerMapping.class.getSimpleName()).expectHeader()
				.valueEquals(ROUTE_ID_HEADER, routeId);
	}

	@Test
	public void hostRouteBackwardsCompatiblePatternWorks() {
		expectHostRoute("www.hostpatternarg.org", "host_backwards_compatible_test");
	}

	@Test
	public void hostRouteBackwardsCompatibleShortcutWorks() {
		expectHostRoute("www.hostpatternshortcut.org", "host_backwards_compatible_shortcut_test");
	}

	@Test
	public void mulitHostRouteWorks() {
		expectHostRoute("www.hostmulti1.org", "host_multi_test");
		expectHostRoute("www.hostmulti2.org", "host_multi_test");
	}

	@Test
	public void mulitHostRouteDslWorks() {
		expectHostRoute("www.hostmultidsl1.org", "host_multi_dsl");
		expectHostRoute("www.hostmultidsl2.org", "host_multi_dsl");
	}

	@Test
	public void toStringFormat() {
		Config config = new Config().setPatterns(Arrays.asList("pattern1", "pattern2"));
		Predicate predicate = new HostRoutePredicateFactory().apply(config);
		assertThat(predicate.toString()).contains("pattern1").contains("pattern2");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("host_multi_dsl", r -> r.host("**.hostmultidsl1.org", "**.hostmultidsl2.org")
					.filters(f -> f.prefixPath("/httpbin")).uri(uri)).build();
		}

	}

}
