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

package org.springframework.cloud.gateway.server.mvc.test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.http.server.LocalTestWebServer;
import org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfiguration(after = GatewayServerMvcAutoConfiguration.class)
public class TestAutoConfiguration {

	@Bean
	@Lazy // so env has the chance to get local.server.port
	public RestTestClient restTestClient(Environment env) {
		String port = env.getProperty("local.server.port", "8080");
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Bean
	public TestController testController() {
		return new TestController();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	static class TestRestTemplateConfig {

		@Bean(name = "org.springframework.boot.resttestclient.TestRestTemplate")
		@ConditionalOnMissingBean
		TestRestTemplate testRestTemplate(ObjectProvider<RestTemplateBuilder> builderProvider,
				ApplicationContext applicationContext) {
			RestTemplateBuilder builder = builderProvider.getIfAvailable(RestTemplateBuilder::new);
			LocalTestWebServer localTestWebServer = LocalTestWebServer.obtain(applicationContext);
			TestRestTemplate template = new TestRestTemplate(builder, null, null,
					httpClientOptions(localTestWebServer.scheme()));
			template.setUriTemplateHandler(localTestWebServer.uriBuilderFactory());
			return template;
		}

		private TestRestTemplate.HttpClientOption[] httpClientOptions(LocalTestWebServer.Scheme scheme) {
			return switch (scheme) {
				case HTTP -> new TestRestTemplate.HttpClientOption[] {};
				case HTTPS -> new TestRestTemplate.HttpClientOption[] { TestRestTemplate.HttpClientOption.SSL };
			};
		}

	}

}
