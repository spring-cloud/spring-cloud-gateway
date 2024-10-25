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

package org.springframework.cloud.gateway.server.mvc.test;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.client.DefaultTestRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@AutoConfiguration(after = GatewayServerMvcAutoConfiguration.class)
public class TestAutoConfiguration {

	@Bean
	RestTemplateCustomizer testRestClientRestTemplateCustomizer() {
		return restTemplate -> restTemplate.setClientHttpRequestInitializers(List.of(request -> {
			if (!request.getHeaders().containsKey(HttpHeaders.ACCEPT)) {
				request.getHeaders().setAccept(List.of(MediaType.ALL));
			}
		}));
	}

	@Bean
	public DefaultTestRestClient testRestClient(@Lazy TestRestTemplate testRestTemplate, Environment env) {
		return new DefaultTestRestClient(testRestTemplate, new LocalHostUriBuilderFactory(env), result -> {
		});
	}

	@Bean
	public TestController testController() {
		return new TestController();
	}

}
