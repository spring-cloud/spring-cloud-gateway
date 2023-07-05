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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcPropertiesBeanDefinitionRegistrar;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.handler.ClientHttpRequestFactoryProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateDiscoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;

@AutoConfiguration(after = RestTemplateAutoConfiguration.class)
@Import(GatewayMvcPropertiesBeanDefinitionRegistrar.class)
public class GatewayServerMvcAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ProxyExchange.class)
	public ClientHttpRequestFactoryProxyExchange clientHttpRequestFactoryProxyExchange(
			ClientHttpRequestFactory requestFactory) {
		return new ClientHttpRequestFactoryProxyExchange(requestFactory);
	}

	// Make default when reflection is no longer needed to function
	// @Bean
	@ConditionalOnMissingBean(ProxyExchange.class)
	public RestClientProxyExchange restClientProxyExchange(ClientHttpRequestFactory requestFactory) {
		return new RestClientProxyExchange(requestFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	public ForwardedRequestHeadersFilter forwardedRequestHeadersFilter() {
		return new ForwardedRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public ClientHttpRequestFactory gatewayClientHttpRequestFactory(RestTemplateBuilder restTemplateBuilder) {
		// TODO: set property if jdk HttpClient
		// TODO: temporarily force Jdk HttpClient, copied from
		// https://github.com/spring-projects/spring-boot/pull/36118
		String restrictedHeaders = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
		if (!StringUtils.hasText(restrictedHeaders)) {
			System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		}
		else if (StringUtils.hasText(restrictedHeaders) && !restrictedHeaders.contains("host")) {
			System.setProperty("jdk.httpclient.allowRestrictedHeaders", restrictedHeaders + ",host");
		}
		return restTemplateBuilder.requestFactory(settings -> {
			java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
			if (settings.connectTimeout() != null) {
				builder.connectTimeout(settings.connectTimeout());
			}
			if (settings.sslBundle() != null) {
				builder.sslContext(settings.sslBundle().createSslContext());
			}
			java.net.http.HttpClient httpClient = builder.build();
			JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
			if (settings.readTimeout() != null) {
				requestFactory.setReadTimeout(settings.readTimeout());
			}
			return requestFactory;
		}).buildRequestFactory();
	}

	@Bean
	@ConditionalOnMissingBean
	public GatewayMvcProperties gatewayMvcProperties() {
		return new GatewayMvcProperties();
	}

	@Bean
	public PredicateDiscoverer predicateDiscoverer() {
		return new PredicateDiscoverer();
	}

	@Bean
	@ConditionalOnMissingBean
	public ProxyExchangeHandlerFunction proxyExchangeHandlerFunction(ProxyExchange proxyExchange,
			ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFilters,
			ObjectProvider<ResponseHttpHeadersFilter> responseHttpHeadersFilters) {
		return new ProxyExchangeHandlerFunction(proxyExchange, requestHttpHeadersFilters, responseHttpHeadersFilters);
	}

	@Bean
	@ConditionalOnMissingBean
	public RemoveHopByHopRequestHeadersFilter removeHopByHopRequestHeadersFilter() {
		return new RemoveHopByHopRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public RemoveHopByHopResponseHeadersFilter removeHopByHopResponseHeadersFilter() {
		return new RemoveHopByHopResponseHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public XForwardedRequestHeadersFilter xForwardedRequestHeadersFilter() {
		return new XForwardedRequestHeadersFilter();
	}

}
