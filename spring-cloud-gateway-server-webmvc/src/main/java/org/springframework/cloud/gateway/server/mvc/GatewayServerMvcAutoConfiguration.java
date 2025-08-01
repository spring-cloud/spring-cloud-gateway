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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.autoconfigure.AbstractHttpRequestFactoryProperties.Factory;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties.Apiversion;
import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplierBeanPostProcessor;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcPropertiesBeanDefinitionRegistrar;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcRuntimeHintsProcessor;
import org.springframework.cloud.gateway.server.mvc.config.RouterFunctionHolderFactory;
import org.springframework.cloud.gateway.server.mvc.filter.FilterAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.filter.FilterBeanFactoryDiscoverer;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveContentLengthRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHttp2StatusResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TransferEncodingNormalizationRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TrustedProxies;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilterProperties;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctionAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateBeanFactoryDiscoverer;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateDiscoverer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.accept.ApiVersionDeprecationHandler;
import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.MediaTypeParamApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.QueryApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.client.RestClient;

/**
 * AutoConfiguration for Spring Cloud Gateway MVC server.
 *
 * @author Spencer Gibb
 * @author Jürgen Wißkirchen
 */
@AutoConfiguration(after = { HttpClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
		RestClientAutoConfiguration.class, FilterAutoConfiguration.class, HandlerFunctionAutoConfiguration.class,
		PredicateAutoConfiguration.class })
@ConditionalOnProperty(name = GatewayMvcProperties.PREFIX + ".enabled", matchIfMissing = true)
@Import(GatewayMvcPropertiesBeanDefinitionRegistrar.class)
public class GatewayServerMvcAutoConfiguration {

	@Bean
	public static ArgumentSupplierBeanPostProcessor argumentSupplierBeanPostProcessor(
			ApplicationEventPublisher publisher) {
		return new ArgumentSupplierBeanPostProcessor(publisher);
	}

	@Bean
	public RouterFunctionHolderFactory routerFunctionHolderFactory(Environment env, BeanFactory beanFactory,
			FilterBeanFactoryDiscoverer filterBeanFactoryDiscoverer,
			PredicateBeanFactoryDiscoverer predicateBeanFactoryDiscoverer) {
		return new RouterFunctionHolderFactory(env, beanFactory, filterBeanFactoryDiscoverer,
				predicateBeanFactoryDiscoverer);
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
	@Conditional(TrustedProxies.ForwardedTrustedProxiesCondition.class)
	public ForwardedRequestHeadersFilter forwardedRequestHeadersFilter(GatewayMvcProperties properties) {
		return new ForwardedRequestHeadersFilter(properties.getTrustedProxies());
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
	@Conditional(TrustedProxies.XForwardedTrustedProxiesCondition.class)
	public XForwardedRequestHeadersFilter xForwardedRequestHeadersFilter(XForwardedRequestHeadersFilterProperties props,
			GatewayMvcProperties gatewayMvcProperties) {
		return new XForwardedRequestHeadersFilter(props, gatewayMvcProperties.getTrustedProxies());
	}

	@Bean
	public XForwardedRequestHeadersFilterProperties xForwardedRequestHeadersFilterProperties() {
		return new XForwardedRequestHeadersFilterProperties();
	}

	@Bean
	static GatewayMvcRuntimeHintsProcessor gatewayMvcRuntimeHintsProcessor() {
		return new GatewayMvcRuntimeHintsProcessor();
	}

	@Bean
	GatewayServerWebMvcBeanPostProcessor gatewayServerWebMvcBeanPostProcessor(
			ObjectProvider<WebMvcProperties> properties,
			ObjectProvider<ApiVersionDeprecationHandler> deprecationHandlerProvider,
			ObjectProvider<ApiVersionParser<?>> versionParserProvider,
			ObjectProvider<ApiVersionResolver> versionResolvers) {
		return new GatewayServerWebMvcBeanPostProcessor(
				properties.getIfAvailable(WebMvcProperties::new).getApiversion(),
				deprecationHandlerProvider.getIfAvailable(), versionParserProvider.getIfAvailable(),
				versionResolvers.orderedStream().toList());
	}

	static class GatewayHttpClientEnvironmentPostProcessor implements EnvironmentPostProcessor {

		static final boolean APACHE = ClassUtils.isPresent("org.apache.hc.client5.http.impl.classic.HttpClients", null);
		static final boolean JETTY = ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient", null);
		static final boolean REACTOR_NETTY = ClassUtils.isPresent("reactor.netty.http.client.HttpClient", null);
		static final boolean JDK = ClassUtils.isPresent("java.net.http.HttpClient", null);
		static final boolean HIGHER_PRIORITY = APACHE || JETTY || REACTOR_NETTY;
		static final String SPRING_REDIRECTS_PROPERTY = "spring.http.client.redirects";
		static final String SPRING_HTTP_FACTORY_PROPERTY = "spring.http.client.factory";

		@Override
		public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
			HttpRedirects redirects = environment.getProperty(SPRING_REDIRECTS_PROPERTY, HttpRedirects.class);
			if (redirects == null) {
				// the user hasn't set anything, change the default
				environment.getPropertySources()
					.addFirst(new MapPropertySource("gatewayHttpClientProperties",
							Map.of(SPRING_REDIRECTS_PROPERTY, HttpRedirects.DONT_FOLLOW)));
			}
			Factory factory = environment.getProperty(SPRING_HTTP_FACTORY_PROPERTY, Factory.class);
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

	protected static class GatewayServerWebMvcBeanPostProcessor implements BeanPostProcessor {

		private final Apiversion versionProperties;

		private final ApiVersionDeprecationHandler deprecationHandler;

		private final ApiVersionParser<?> versionParser;

		private final List<ApiVersionResolver> apiVersionResolvers;

		public GatewayServerWebMvcBeanPostProcessor(Apiversion versionProperties,
				ApiVersionDeprecationHandler deprecationHandler, ApiVersionParser<?> versionParser,
				List<ApiVersionResolver> apiVersionResolvers) {
			this.versionProperties = versionProperties;
			this.deprecationHandler = deprecationHandler;
			this.versionParser = (versionParser != null) ? versionParser : new SemanticApiVersionParser();
			this.apiVersionResolvers = apiVersionResolvers;
		}

		@Override
		public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

			// TODO: Use custom ApiVersionConfigurer when able to
			if (bean instanceof ApiVersionStrategy && beanName.equals("mvcApiVersionStrategy")) {
				List<ApiVersionResolver> versionResolvers = new ArrayList<>();
				if (StringUtils.hasText(versionProperties.getUse().getHeader())) {
					versionResolvers.add(request -> request.getHeader(versionProperties.getUse().getHeader()));
				}
				if (!CollectionUtils.isEmpty(versionProperties.getUse().getMediaTypeParameter())) {
					versionProperties.getUse().getMediaTypeParameter().forEach((mediaType, param) -> {
						versionResolvers.add(new MediaTypeParamApiVersionResolver(mediaType, param));
					});
				}
				if (versionProperties.getUse().getPathSegment() != null) {
					versionResolvers.add(new PathApiVersionResolver(versionProperties.getUse().getPathSegment()));
				}
				if (StringUtils.hasText(versionProperties.getUse().getQueryParameter())) {
					versionResolvers.add(new QueryApiVersionResolver(versionProperties.getUse().getQueryParameter()));
				}

				if (apiVersionResolvers != null && !apiVersionResolvers.isEmpty()) {
					versionResolvers.addAll(apiVersionResolvers);
				}

				if (versionResolvers.isEmpty()) {
					return bean;
				}

				Boolean required = versionProperties.getRequired();
				if (required == null) {
					required = false;
				}
				Boolean detectSupported = versionProperties.getDetectSupported();
				if (detectSupported == null) {
					detectSupported = true;
				}
				GatewayApiVersionStrategy strategy = new GatewayApiVersionStrategy(versionResolvers, versionParser,
						required, versionProperties.getDefaultVersion(), detectSupported, deprecationHandler);
				if (!CollectionUtils.isEmpty(versionProperties.getSupported())) {
					strategy.addSupportedVersion(versionProperties.getSupported().toArray(new String[0]));
				}
				return strategy;
			}
			return bean;
		}

	}

	protected static class GatewayApiVersionStrategy extends DefaultApiVersionStrategy {

		public GatewayApiVersionStrategy(List<ApiVersionResolver> versionResolvers, ApiVersionParser<?> versionParser,
				boolean versionRequired, @Nullable String defaultVersion, boolean detectSupportedVersions,
				@Nullable ApiVersionDeprecationHandler deprecationHandler) {
			super(versionResolvers, versionParser, versionRequired, defaultVersion, detectSupportedVersions,
					comparable -> true, deprecationHandler);
		}

	}

}
