/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.gateway.mvc.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Autoconfiguration for the {@link ProxyExchange} argument handler in Spring MVC
 * <code>@RequestMapping</code> methods.
 *
 * @author Dave Syer
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass({ HandlerMethodReturnValueHandler.class })
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyResponseAutoConfiguration implements WebMvcConfigurer {

	@Autowired
	private ApplicationContext context;

	@Bean
	@ConditionalOnMissingBean
	public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver(
			Optional<RestTemplateBuilder> optional, ProxyProperties proxy) {
		RestTemplateBuilder builder = optional.orElse(new RestTemplateBuilder());
		RestTemplate template = builder.build();
		template.setErrorHandler(new NoOpResponseErrorHandler());
		template.getMessageConverters().add(new ByteArrayHttpMessageConverter() {
			@Override
			public boolean supports(Class<?> clazz) {
				return true;
			}
		});
		ProxyExchangeArgumentResolver resolver = new ProxyExchangeArgumentResolver(
				template);
		resolver.setHeaders(proxy.convertHeaders());
		resolver.setSensitive(proxy.getSensitive()); // can be null
		return resolver;
	}

	@Override
	public void addArgumentResolvers(
			List<HandlerMethodArgumentResolver> argumentResolvers) {
		argumentResolvers.add(context.getBean(ProxyExchangeArgumentResolver.class));
	}

	private static class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {

		@Override
		public void handleError(ClientHttpResponse response) throws IOException {
		}

	}
}
