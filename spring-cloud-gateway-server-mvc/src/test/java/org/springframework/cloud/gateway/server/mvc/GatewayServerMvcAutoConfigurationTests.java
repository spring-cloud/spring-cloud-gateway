/*
 * Copyright 2013-2024 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveContentLengthRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TransferEncodingNormalizationRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayServerMvcAutoConfigurationTests {

	@Test
	void filterEnabledPropertiesWork() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GatewayServerMvcAutoConfiguration.class,
				RestTemplateAutoConfiguration.class, RestClientAutoConfiguration.class, SslAutoConfiguration.class))
				.withPropertyValues("spring.cloud.gateway.mvc.form-filter.enabled=false",
						"spring.cloud.gateway.mvc.forwarded-request-headers-filter.enabled=false",
						"spring.cloud.gateway.mvc.remove-content-length-request-headers-filter.enabled=false",
						"spring.cloud.gateway.mvc.remove-hop-by-hop-request-headers-filter.enabled=false",
						"spring.cloud.gateway.mvc.remove-hop-by-hop-response-headers-filter.enabled=false",
						"spring.cloud.gateway.mvc.transfer-encoding-normalization-request-headers-filter.enabled=false",
						"spring.cloud.gateway.mvc.weight-calculator-filter.enabled=false",
						"spring.cloud.gateway.mvc.x-forwarded-request-headers-filter.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(FormFilter.class);
					assertThat(context).doesNotHaveBean(ForwardedRequestHeadersFilter.class);
					assertThat(context).doesNotHaveBean(RemoveContentLengthRequestHeadersFilter.class);
					assertThat(context).doesNotHaveBean(RemoveHopByHopRequestHeadersFilter.class);
					assertThat(context).doesNotHaveBean(RemoveHopByHopResponseHeadersFilter.class);
					assertThat(context).doesNotHaveBean(TransferEncodingNormalizationRequestHeadersFilter.class);
					assertThat(context).doesNotHaveBean(WeightCalculatorFilter.class);
					assertThat(context).doesNotHaveBean(XForwardedRequestHeadersFilter.class);
				});
	}

}
