/*
 * Copyright 2013-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteRefreshListener;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = { "debug=true" }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("propertiesmigrationtests")
public class GatewayPropertiesMigrationTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	GatewayProperties properties;

	@Test
	public void deprecatedPropertiesWork() {
		assertBeanDoesNotExist(AddRequestParameterGatewayFilterFactory.class);
		assertBeanDoesNotExist(AfterRoutePredicateFactory.class);
		assertBeanDoesNotExist(ForwardedHeadersFilter.class);
		assertBeanDoesNotExist(RouteRefreshListener.class);
		assertBeanDoesNotExist(XForwardedHeadersFilter.class);
	}

	@Test
	public void deprecatedRoutePropertiesWork() {
		assertThat(properties.getRoutes()).hasSize(2);
	}

	private void assertBeanDoesNotExist(Class<?> type) {
		assertThat(context.getBeanNamesForType(type)).isEmpty();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

	}

}
