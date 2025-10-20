/*
 * Copyright 2013-present the original author or authors.
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
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@DisabledForJreRange(min = JRE.JAVA_25)
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

	@Test
	public void deprecatedDefaultFiltersPropertiesWork() {
		assertThat(properties.getDefaultFilters()).hasSize(2);
		assertThat(properties.getDefaultFilters().get(0).getName()).isEqualTo("AddRequestHeader");
		assertThat(properties.getDefaultFilters().get(1).getName()).isEqualTo("AddRequestHeader");
	}

	@Test
	public void deprecatedStreamingMediaTypesWork() {
		assertThat(properties.getStreamingMediaTypes()).hasSize(1)
			.containsOnly(new MediaType("application", "activemessage"));
	}

	private void assertBeanDoesNotExist(Class<?> type) {
		assertThat(context.getBeanNamesForType(type)).isEmpty();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

	}

}
