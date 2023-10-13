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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplierBeanPostProcessor;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcPropertiesBeanDefinitionRegistrar;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveContentLengthRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TransferEncodingNormalizationRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateDiscoverer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = { RestTemplateAutoConfiguration.class, RestClientAutoConfiguration.class })
@ConditionalOnProperty(name = "spring.cloud.gateway.mvc.enabled", matchIfMissing = true)
@Import(GatewayMvcPropertiesBeanDefinitionRegistrar.class)
public class GatewayServerMvcAutoConfiguration {

	@Bean
	public static ArgumentSupplierBeanPostProcessor argumentSupplierBeanPostProcessor(
			ApplicationEventPublisher publisher) {
		return new ArgumentSupplierBeanPostProcessor(publisher);
	}

	@Bean
	public RestClientCustomizer gatewayRestClientCustomizer(ClientHttpRequestFactory requestFactory) {
		return restClientBuilder -> restClientBuilder.requestFactory(requestFactory);
	}

	@Bean
	@ConditionalOnMissingBean(ProxyExchange.class)
	public RestClientProxyExchange restClientProxyExchange(RestClient.Builder restClientBuilder) {
		return new RestClientProxyExchange(restClientBuilder.build());
	}

	@Bean
	@ConditionalOnMissingBean
	public FormFilter formFilter() {
		return new FormFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public ForwardedRequestHeadersFilter forwardedRequestHeadersFilter() {
		return new ForwardedRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public ClientHttpRequestFactory gatewayClientHttpRequestFactory(GatewayMvcProperties gatewayMvcProperties,
			SslBundles sslBundles) {
		GatewayMvcProperties.HttpClient properties = gatewayMvcProperties.getHttpClient();

		SslBundle sslBundle = null;
		if (StringUtils.hasText(properties.getSslBundle())) {
			sslBundle = sslBundles.getBundle(properties.getSslBundle());
		}
		ClientHttpRequestFactorySettings settings = new ClientHttpRequestFactorySettings(properties.getConnectTimeout(),
				properties.getReadTimeout(), sslBundle);

		if (properties.getType() == GatewayMvcProperties.HttpClientType.JDK) {
			// TODO: customize restricted headers
			String restrictedHeaders = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
			if (!StringUtils.hasText(restrictedHeaders)) {
				System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
			}
			else if (StringUtils.hasText(restrictedHeaders) && !restrictedHeaders.contains("host")) {
				System.setProperty("jdk.httpclient.allowRestrictedHeaders", restrictedHeaders + ",host");
			}

			return ClientHttpRequestFactories.get(JdkClientHttpRequestFactory.class, settings);
		}

		// Autodetect
		return ClientHttpRequestFactories.get(settings);
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
	public RemoveContentLengthRequestHeadersFilter removeContentLengthRequestHeadersFilter() {
		return new RemoveContentLengthRequestHeadersFilter();
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
	public TransferEncodingNormalizationRequestHeadersFilter transferEncodingNormalizationRequestHeadersFilter() {
		return new TransferEncodingNormalizationRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public WeightCalculatorFilter weightCalculatorFilter() {
		return new WeightCalculatorFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public XForwardedRequestHeadersFilter xForwardedRequestHeadersFilter() {
		return new XForwardedRequestHeadersFilter();
	}

}
