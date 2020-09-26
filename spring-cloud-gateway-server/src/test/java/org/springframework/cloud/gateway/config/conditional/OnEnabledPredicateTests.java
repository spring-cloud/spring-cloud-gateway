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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

import static org.assertj.core.api.Assertions.assertThat;

class OnEnabledPredicateTests {

	private OnEnabledPredicate onEnabledPredicate;

	@BeforeEach
	void setUp() {
		this.onEnabledPredicate = new OnEnabledPredicate();
	}

	@Test
	void shouldNormalizePredicatesNames() {
		List<Class<? extends RoutePredicateFactory<?>>> predicates = Arrays.asList(AfterRoutePredicateFactory.class,
				CloudFoundryRouteServiceRoutePredicateFactory.class, ReadBodyRoutePredicateFactory.class,
				RemoteAddrRoutePredicateFactory.class);

		List<String> resultNames = predicates.stream().map(onEnabledPredicate::normalizeComponentName)
				.collect(Collectors.toList());

		List<String> expectedNames = Stream.of("after", "cloud-foundry-route-service", "read-body", "remote-addr")
				.map(s -> "predicate." + s).collect(Collectors.toList());

		assertThat(resultNames).isEqualTo(expectedNames);
	}

}
