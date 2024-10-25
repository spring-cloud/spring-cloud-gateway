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

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientProperties.Factory;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings.Redirects;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplierBeanPostProcessor;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcAotRuntimeHintsRegistrar;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcPropertiesBeanDefinitionRegistrar;
import org.springframework.cloud.gateway.server.mvc.config.RouterFunctionHolderFactory;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveContentLengthRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHttp2StatusResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TransferEncodingNormalizationRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilterProperties;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateDiscoverer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * AutoConfiguration for Spring Cloud Gateway MVC server.
 *
 * @author Spencer Gibb
 * @author Jürgen Wißkirchen
 */
@AutoConfiguration(after = { HttpClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
		RestClientAutoConfiguration.class })
@ConditionalOnProperty(name = "spring.cloud.gateway.mvc.enabled", matchIfMissing = true)
@Import(GatewayMvcPropertiesBeanDefinitionRegistrar.class)
@ImportRuntimeHints(GatewayMvcAotRuntimeHintsRegistrar.class)
public class GatewayServerMvcAutoConfiguration {

	@Bean
	public static ArgumentSupplierBeanPostProcessor argumentSupplierBeanPostProcessor(
			ApplicationEventPublisher publisher) {
		return new ArgumentSupplierBeanPostProcessor(publisher);
	}

	@Bean
	public RouterFunctionHolderFactory routerFunctionHolderFactory(Environment env) {
		return new RouterFunctionHolderFactory(env);
	}

	@Bean
	public RestClientCustomizer gatewayRestClientCustomizer(
			ObjectProvider<ClientHttpRequestFactory> requestFactoryProvider) {
		return restClientBuilder -> {
			// for backwards compatibility if user overrode
			requestFactoryProvider.ifAvailable(restClientBuilder::requestFactory);
		};
	}

	@Bean
	@ConditionalOnMissingBean(ProxyExchange.class)
	public RestClientProxyExchange restClientProxyExchange(RestClient.Builder restClientBuilder,
			GatewayMvcProperties properties) {
		return new RestClientProxyExchange(restClientBuilder.build(), properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX, name = "form-filter.enabled", matchIfMissing = true)
	public FormFilter formFilter() {
		return new FormFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX, name = "forwarded-request-headers-filter.enabled",
			matchIfMissing = true)
	public ForwardedRequestHeadersFilter forwardedRequestHeadersFilter() {
		return new ForwardedRequestHeadersFilter();
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
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX,
			name = "remove-content-length-request-headers-filter.enabled", matchIfMissing = true)
	public RemoveContentLengthRequestHeadersFilter removeContentLengthRequestHeadersFilter() {
		return new RemoveContentLengthRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX,
			name = "remove-http2-status-response-headers-filter.enabled", matchIfMissing = true)
	public RemoveHttp2StatusResponseHeadersFilter removeHttp2StatusResponseHeadersFilter() {
		return new RemoveHttp2StatusResponseHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX,
			name = "remove-hop-by-hop-request-headers-filter.enabled", matchIfMissing = true)
	public RemoveHopByHopRequestHeadersFilter removeHopByHopRequestHeadersFilter() {
		return new RemoveHopByHopRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX,
			name = "remove-hop-by-hop-response-headers-filter.enabled", matchIfMissing = true)
	public RemoveHopByHopResponseHeadersFilter removeHopByHopResponseHeadersFilter() {
		return new RemoveHopByHopResponseHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX,
			name = "transfer-encoding-normalization-request-headers-filter.enabled", matchIfMissing = true)
	public TransferEncodingNormalizationRequestHeadersFilter transferEncodingNormalizationRequestHeadersFilter() {
		return new TransferEncodingNormalizationRequestHeadersFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = GatewayMvcProperties.PREFIX, name = "weight-calculator-filter.enabled",
			matchIfMissing = true)
	public WeightCalculatorFilter weightCalculatorFilter() {
		return new WeightCalculatorFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = XForwardedRequestHeadersFilterProperties.PREFIX, name = ".enabled",
			matchIfMissing = true)
	public XForwardedRequestHeadersFilter xForwardedRequestHeadersFilter(
			XForwardedRequestHeadersFilterProperties props) {
		return new XForwardedRequestHeadersFilter(props);
	}

	@Bean
	public XForwardedRequestHeadersFilterProperties xForwardedRequestHeadersFilterProperties() {
		return new XForwardedRequestHeadersFilterProperties();
	}

	static class GatewayHttpClientEnvironmentPostProcessor implements EnvironmentPostProcessor {

		static final boolean APACHE = ClassUtils.isPresent("org.apache.hc.client5.http.impl.classic.HttpClients", null);
		static final boolean JETTY = ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient", null);
		static final boolean REACTOR_NETTY = ClassUtils.isPresent("reactor.netty.http.client.HttpClient", null);
		static final boolean JDK = ClassUtils.isPresent("java.net.http.HttpClient", null);
		static final boolean HIGHER_PRIORITY = APACHE || JETTY || REACTOR_NETTY;

		@Override
		public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
			Redirects redirects = environment.getProperty("spring.http.client.redirects", Redirects.class);
			if (redirects == null) {
				// the user hasn't set anything, change the default
				environment.getPropertySources()
					.addFirst(new MapPropertySource("gatewayHttpClientProperties",
							Map.of("spring.http.client.redirects", Redirects.DONT_FOLLOW)));
			}
			Factory factory = environment.getProperty("spring.http.client.factory", Factory.class);
			boolean setJdkHttpClientProperties = false;

			if (factory == null && !HIGHER_PRIORITY) {
				// autodetect
				setJdkHttpClientProperties = JDK;
			}
			else if (factory == Factory.JDK) {
				setJdkHttpClientProperties = JDK;
			}

			if (setJdkHttpClientProperties) {
				// TODO: customize restricted headers
				String restrictedHeaders = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
				if (!StringUtils.hasText(restrictedHeaders)) {
					System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
				}
				else if (StringUtils.hasText(restrictedHeaders) && !restrictedHeaders.contains("host")) {
					System.setProperty("jdk.httpclient.allowRestrictedHeaders", restrictedHeaders + ",host");
				}
			}
		}

	}

}
