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

package org.springframework.cloud.gateway.webflux.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

/**
 * Autoconfiguration for the {@link ProxyExchange} argument handler in Spring Webflux
 * <code>@RequestMapping</code> methods.
 *
 * @author Dave Syer
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass({ HandlerMethodReturnValueHandler.class })
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyResponseAutoConfiguration implements WebFluxConfigurer {

	@Autowired
	private ApplicationContext context;

	@Bean
	@ConditionalOnMissingBean
	public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver(
			Optional<WebClient.Builder> optional, ProxyProperties proxy) {
		WebClient.Builder builder = optional.orElse(WebClient.builder());
		WebClient template = builder.build();
		ProxyExchangeArgumentResolver resolver = new ProxyExchangeArgumentResolver(
				template);
		resolver.setHeaders(proxy.convertHeaders());
		resolver.setSensitive(proxy.getSensitive()); // can be null
		return resolver;
	}

	@Override
	public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
		WebFluxConfigurer.super.configureArgumentResolvers(configurer);
		configurer
				.addCustomResolver(context.getBean(ProxyExchangeArgumentResolver.class));
	}
}
