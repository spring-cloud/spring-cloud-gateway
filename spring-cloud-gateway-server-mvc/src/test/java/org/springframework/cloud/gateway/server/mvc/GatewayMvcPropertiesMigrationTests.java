/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveContentLengthRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHttp2StatusResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TransferEncodingNormalizationRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = {}, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("propertiesmigrationtests")
public class GatewayMvcPropertiesMigrationTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	GatewayMvcProperties properties;

	@SuppressWarnings("rawtypes")
	@Test
	public void deprecatedFilterEnabledPropertiesWork() {
		assertBeanDoesNotExist(FormFilter.class);
		assertBeanDoesNotExist(ForwardedRequestHeadersFilter.class);
		assertBeanDoesNotExist(RemoveContentLengthRequestHeadersFilter.class);
		assertBeanDoesNotExist(RemoveHopByHopRequestHeadersFilter.class);
		assertBeanDoesNotExist(RemoveHopByHopResponseHeadersFilter.class);
		assertBeanDoesNotExist(RemoveHttp2StatusResponseHeadersFilter.class);
		assertBeanDoesNotExist(TransferEncodingNormalizationRequestHeadersFilter.class);
		assertBeanDoesNotExist(WeightCalculatorFilter.class);
		assertBeanDoesNotExist(XForwardedRequestHeadersFilter.class);
	}

	@Test
	public void deprecatedRoutePropertiesWork() {
		assertThat(properties.getRoutes()).hasSize(2);
		assertThat(properties.getRoutesMap()).hasSize(2);
	}

	private void assertBeanDoesNotExist(Class<?> type) {
		assertThat(context.getBeanNamesForType(type)).isEmpty();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

	}

}
