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
import org.springframework.cloud.gateway.server.mvc.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;

@AutoConfiguration(after = RestTemplateAutoConfiguration.class)
public class GatewayServerMvcAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ClientHttpRequestFactory gatewayClientHttpRequestFactory(RestTemplateBuilder builder) {
		return builder.buildRequestFactory();
	}

	@Bean
	@ConditionalOnMissingBean
	public ClientHttpRequestFactoryProxyExchange clientHttpRequestFactoryProxyExchange(
			ClientHttpRequestFactory requestFactory) {
		return new ClientHttpRequestFactoryProxyExchange(requestFactory);
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

}
