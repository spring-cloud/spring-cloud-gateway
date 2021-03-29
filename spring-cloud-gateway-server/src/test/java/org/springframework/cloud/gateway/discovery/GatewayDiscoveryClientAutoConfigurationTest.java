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

package org.springframework.cloud.gateway.discovery;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;

public class GatewayDiscoveryClientAutoConfigurationTest {

	private static final String SERVICE_ID = "foo";

	private static final String BASE_URI = "/" + SERVICE_ID;

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private static final SimpleEvaluationContext CONTEXT = SimpleEvaluationContext.forReadOnlyDataBinding()
			.withInstanceMethods().build();

	private static final FilterDefinition DEFAULT_FILTER = GatewayDiscoveryClientAutoConfiguration.initFilters().get(0);

	private ServiceInstance serviceInstance;

	@BeforeEach
	public void buildServiceInstance() {
		this.serviceInstance = mock(ServiceInstance.class);
		when(this.serviceInstance.getServiceId()).thenReturn(SERVICE_ID);
	}

	@Test
	public void defaultRewritePathShouldHandleEmptyRemainingWithoutSlash() {
		String expectedRemotePath = "/";

		final String result = replace(BASE_URI);

		assertThat(result).isEqualTo(expectedRemotePath);
	}

	@Test
	public void defaultRewritePathShouldHandleEmptyRemainingWithSlash() {
		String extraUri = "/";

		final String result = replace(BASE_URI + extraUri);

		assertThat(result).isEqualTo(extraUri);
	}

	@Test
	public void defaultRewritePathShouldHandleNonEmptyRemainingPath() {
		String extraUri = "/some/additional/uri";

		final String result = replace(BASE_URI + extraUri);

		assertThat(result).isEqualTo(extraUri);
	}

	private String replace(String enteringPath) {
		return enteringPath.replaceAll(evaluateExpression(DEFAULT_FILTER.getArgs().get(REGEXP_KEY)),
				evaluateExpression(DEFAULT_FILTER.getArgs().get(REPLACEMENT_KEY)));
	}

	private String evaluateExpression(String expression) {
		return Objects
				.requireNonNull(PARSER.parseExpression(expression).getValue(CONTEXT, serviceInstance, String.class));
	}

}
