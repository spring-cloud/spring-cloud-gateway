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

package org.springframework.cloud.gateway.config.conditional;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

public class DisableBuiltInPredicatesTests {

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	@Nested
	@SpringBootTest(classes = Config.class)
	public class RoutePredicateDefault {

		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldInjectBuiltInPredicates() {
			assertThat(predicates).hasSizeGreaterThanOrEqualTo(13);
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.predicate.after.enabled=false",
					"spring.cloud.gateway.predicate.before.enabled=false" })
	@ActiveProfiles("disable-components")
	public class DisableSpecificsPredicatesByProperty {

		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldInjectOnlyEnabledBuiltInPredicates() {
			assertThat(predicates).hasSizeGreaterThan(0);
			assertThat(predicates).allSatisfy(filter -> assertThat(filter)
					.isNotInstanceOfAny(AfterRoutePredicateFactory.class, BeforeRoutePredicateFactory.class));
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class, properties = { "spring.cloud.gateway.predicate.after.enabled=false",
			"spring.cloud.gateway.predicate.before.enabled=false",
			"spring.cloud.gateway.predicate.between.enabled=false",
			"spring.cloud.gateway.predicate.cookie.enabled=false",
			"spring.cloud.gateway.predicate.header.enabled=false", "spring.cloud.gateway.predicate.host.enabled=false",
			"spring.cloud.gateway.predicate.method.enabled=false", "spring.cloud.gateway.predicate.path.enabled=false",
			"spring.cloud.gateway.predicate.query.enabled=false",
			"spring.cloud.gateway.predicate.read-body.enabled=false",
			"spring.cloud.gateway.predicate.remote-addr.enabled=false",
			"spring.cloud.gateway.predicate.xforwarded-remote-addr.enabled=false",
			"spring.cloud.gateway.predicate.weight.enabled=false",
			"spring.cloud.gateway.predicate.cloud-foundry-route-service.enabled=false" })
	@ActiveProfiles("disable-components")
	public class DisableAllPredicatesByProperty {

		@Autowired(required = false)
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldDisableAllBuiltInPredicates() {
			assertThat(predicates).isNull();
		}

	}

}
