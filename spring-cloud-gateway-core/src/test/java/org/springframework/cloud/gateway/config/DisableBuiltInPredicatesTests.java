/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class DisableBuiltInPredicatesTests {

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class RoutePredicateDefault {

		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldInjectBuiltInPredicates() {
			assertThat(predicates).hasSizeGreaterThanOrEqualTo(13);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.AfterRoute.enabled=false",
					"spring.cloud.gateway.BeforeRoute.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableSpecificsPredicatesByProperty {

		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldInjectOnlyEnabledBuiltInPredicates() {
			assertThat(predicates).hasSizeGreaterThan(0);
			assertThat(predicates).allSatisfy(filter -> assertThat(filter)
					.isNotInstanceOfAny(AfterRoutePredicateFactory.class,
							BeforeRoutePredicateFactory.class));
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.AfterRoute.enabled=false",
					"spring.cloud.gateway.BeforeRoute.enabled=false",
					"spring.cloud.gateway.BetweenRoute.enabled=false",
					"spring.cloud.gateway.CookieRoute.enabled=false",
					"spring.cloud.gateway.HeaderRoute.enabled=false",
					"spring.cloud.gateway.HostRoute.enabled=false",
					"spring.cloud.gateway.MethodRoute.enabled=false",
					"spring.cloud.gateway.PathRoute.enabled=false",
					"spring.cloud.gateway.QueryRoute.enabled=false",
					"spring.cloud.gateway.ReadBody.enabled=false",
					"spring.cloud.gateway.RemoteAddrRoute.enabled=false",
					"spring.cloud.gateway.WeightRoute.enabled=false",
					"spring.cloud.gateway.CloudFoundryRoute.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableAllPredicatesByProperty {

		@Autowired(required = false)
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldDisableAllBuiltInPredicates() {
			assertThat(predicates).isNull();
		}

	}

}
