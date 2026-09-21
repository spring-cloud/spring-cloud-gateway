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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("routerfunctionholderfactorybeanresolution")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
class RouterFunctionHolderFactoryBeanResolutionTests {
	private static final String X_CONFIGURED_ARGUMENT = "X-Configured-Argument";
	private static final String X_CUSTOM_FILTER_FUNCTION_BEAN_NAME = "X-Custom-Filter-Function-Bean-Name";
	private static final String X_CUSTOM_FILTER_SERVICE_BEAN_NAME = "X-Custom-Filter-Service-Bean-Name";
	private static final String X_CUSTOM_FILTER_SERVICE_CALL_COUNT = "X-Custom-Filter-Service-Call-Count";

	private static final String X_CUSTOM_FILTER_FUNCTION_BEAN_NAME_VALUE = "bean-context-operation-method-test-filter-function";
	private static final String X_CUSTOM_FILTER_SERVICE_BEAN_NAME_VALUE = "bean-context-operation-method-test-filter-service";

	@Autowired
	RestTestClient restClient;

	@Test
	void nonStaticOperationMethodUsesBean() {
		// Given: CustomBeanFilterFilterFunctions is registered as a bean
		// When: Route uses CustomBeanFilter which is a non-static method
		for (int i = 1; i <= 3; i++) {
			String callCount = "" + i;
			restClient.get()
					.uri("/anything/bean-context")
					.exchange()
					.expectStatus()
					.isOk()
					.expectBody(Map.class)
					.consumeWith(res -> {
						Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
						assertThat(headers).containsEntry(X_CUSTOM_FILTER_FUNCTION_BEAN_NAME, X_CUSTOM_FILTER_FUNCTION_BEAN_NAME_VALUE);
						assertThat(headers).containsEntry(X_CUSTOM_FILTER_SERVICE_BEAN_NAME, X_CUSTOM_FILTER_SERVICE_BEAN_NAME_VALUE);
						assertThat(headers).containsEntry(X_CUSTOM_FILTER_SERVICE_CALL_COUNT, callCount);
						assertThat(headers).containsKey(X_CONFIGURED_ARGUMENT);
					});
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	protected static class TestConfiguration {
		@Bean(X_CUSTOM_FILTER_SERVICE_BEAN_NAME_VALUE)
		public CustomFilterService customFilterService() {
			return new CustomFilterService();
		}

		@Bean(X_CUSTOM_FILTER_FUNCTION_BEAN_NAME_VALUE)
		public CustomFilterFunctions customBeanFilterFunctions(
				@Qualifier(X_CUSTOM_FILTER_SERVICE_BEAN_NAME_VALUE)
				CustomFilterService customFilterService
		) {
			return new CustomFilterFunctions(customFilterService);
		}
	}

	static class CustomFilterService implements BeanNameAware {
		private String beanName;
		private int callCount = 0;

		@Override
		public void setBeanName(String name) {
			this.beanName = name;
		}

		ServerRequest process(ServerRequest request) {
			callCount++;
			return ServerRequest.from(request)
					.header(X_CUSTOM_FILTER_SERVICE_BEAN_NAME, beanName)
					.header(X_CUSTOM_FILTER_SERVICE_CALL_COUNT, "" + callCount).build();
		}
	}

	static class CustomFilterFunctions extends SimpleFilterSupplier implements BeanNameAware {
		private final CustomFilterService service;
		private String beanName;

		@Override
		public void setBeanName(String name) {
			this.beanName = name;
		}

		public CustomFilterFunctions(CustomFilterService service) {
			super(CustomFilterFunctions.class);
			this.service = service;
		}

		@Configurable
		public HandlerFilterFunction<ServerResponse, ServerResponse> customBeanFilter(Config config) {
			return (request, next) ->
			{
				ServerRequest serviceProcessed = service.process(request);
				ServerRequest filterProcessed = ServerRequest.from(serviceProcessed)
						.header(X_CUSTOM_FILTER_FUNCTION_BEAN_NAME, beanName)
						.header(X_CONFIGURED_ARGUMENT, config.getConfiguredArgument()).build();
				return next.handle(filterProcessed);
			};
		}

		static class Config {
			private final String configuredArgument;

			public Config(String configuredArgument) {
				this.configuredArgument = configuredArgument;
			}

			public String getConfiguredArgument() {
				return configuredArgument;
			}
		}
	}
}



