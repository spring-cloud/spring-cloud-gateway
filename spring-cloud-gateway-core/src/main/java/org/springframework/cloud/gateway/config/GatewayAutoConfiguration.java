/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import com.netflix.hystrix.HystrixObservableCommand;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.ProxyProvider;
import rx.RxReactiveStreams;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.NettyWebServerFactoryCustomizer;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.actuate.GatewayLegacyControllerEndpoint;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.ForwardPathFilter;
import org.springframework.cloud.gateway.filter.ForwardRoutingFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.RemoveCachedBodyFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SaveSessionGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersProperties;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.GzipMessageBodyResolver;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveHopByHopHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.route.CachingRouteLocator;
import org.springframework.cloud.gateway.route.CompositeRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.CompositeRouteLocator;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.RouteRefreshListener;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.StringToZonedDateTimeConverter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.DISABLED;
import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.FIXED;

/**
 * @author Spencer Gibb
 * @author Ziemowit Stolarczyk
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@EnableConfigurationProperties
@AutoConfigureBefore({ HttpHandlerAutoConfiguration.class,
		WebFluxAutoConfiguration.class })
@AutoConfigureAfter({ GatewayLoadBalancerClientAutoConfiguration.class,
		GatewayClassPathWarningAutoConfiguration.class })
@ConditionalOnClass(DispatcherHandler.class)
public class GatewayAutoConfiguration {

	@Bean
	public StringToZonedDateTimeConverter stringToZonedDateTimeConverter() {
		return new StringToZonedDateTimeConverter();
	}

	@Bean
	public RouteLocatorBuilder routeLocatorBuilder(
			ConfigurableApplicationContext context) {
		return new RouteLocatorBuilder(context);
	}

	@Bean
	@ConditionalOnMissingBean
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(
			GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	@Bean
	@ConditionalOnMissingBean(RouteDefinitionRepository.class)
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}

	@Bean
	@Primary
	public RouteDefinitionLocator routeDefinitionLocator(
			List<RouteDefinitionLocator> routeDefinitionLocators) {
		return new CompositeRouteDefinitionLocator(
				Flux.fromIterable(routeDefinitionLocators));
	}

	@Bean
	public ConfigurationService gatewayConfigurationService(BeanFactory beanFactory,
			@Qualifier("webFluxConversionService") ObjectProvider<ConversionService> conversionService,
			ObjectProvider<Validator> validator) {
		return new ConfigurationService(beanFactory, conversionService, validator);
	}

	@Bean
	public RouteLocator routeDefinitionRouteLocator(GatewayProperties properties,
			List<GatewayFilterFactory> gatewayFilters,
			List<RoutePredicateFactory> predicates,
			RouteDefinitionLocator routeDefinitionLocator,
			ConfigurationService configurationService) {
		return new RouteDefinitionRouteLocator(routeDefinitionLocator, predicates,
				gatewayFilters, properties, configurationService);
	}

	@Bean
	@Primary
	@ConditionalOnMissingBean(name = "cachedCompositeRouteLocator")
	// TODO: property to disable composite?
	public RouteLocator cachedCompositeRouteLocator(List<RouteLocator> routeLocators) {
		return new CachingRouteLocator(
				new CompositeRouteLocator(Flux.fromIterable(routeLocators)));
	}

	@Bean
	public RouteRefreshListener routeRefreshListener(
			ApplicationEventPublisher publisher) {
		return new RouteRefreshListener(publisher);
	}

	@Bean
	public FilteringWebHandler filteringWebHandler(List<GlobalFilter> globalFilters) {
		return new FilteringWebHandler(globalFilters);
	}

	@Bean
	public GlobalCorsProperties globalCorsProperties() {
		return new GlobalCorsProperties();
	}

	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(
			FilteringWebHandler webHandler, RouteLocator routeLocator,
			GlobalCorsProperties globalCorsProperties, Environment environment) {
		return new RoutePredicateHandlerMapping(webHandler, routeLocator,
				globalCorsProperties, environment);
	}

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	// ConfigurationProperty beans

	@Bean
	public SecureHeadersProperties secureHeadersProperties() {
		return new SecureHeadersProperties();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.forwarded.enabled",
			matchIfMissing = true)
	public ForwardedHeadersFilter forwardedHeadersFilter() {
		return new ForwardedHeadersFilter();
	}

	// HttpHeaderFilter beans

	@Bean
	public RemoveHopByHopHeadersFilter removeHopByHopHeadersFilter() {
		return new RemoveHopByHopHeadersFilter();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.x-forwarded.enabled",
			matchIfMissing = true)
	public XForwardedHeadersFilter xForwardedHeadersFilter() {
		return new XForwardedHeadersFilter();
	}

	// GlobalFilter beans

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.AdaptCachedBody.enabled",
			matchIfMissing = true)
	public AdaptCachedBodyGlobalFilter adaptCachedBodyGlobalFilter() {
		return new AdaptCachedBodyGlobalFilter();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RemoveCachedBody.enabled",
			matchIfMissing = true)
	public RemoveCachedBodyFilter removeCachedBodyFilter() {
		return new RemoveCachedBodyFilter();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RouteToRequestUrl.enabled",
			matchIfMissing = true)
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.ForwardRouting.enabled",
			matchIfMissing = true)
	public ForwardRoutingFilter forwardRoutingFilter(
			ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new ForwardRoutingFilter(dispatcherHandler);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.ForwardPath.enabled",
			matchIfMissing = true)
	public ForwardPathFilter forwardPathFilter() {
		return new ForwardPathFilter();
	}

	@Bean
	public WebSocketService webSocketService(
			RequestUpgradeStrategy requestUpgradeStrategy) {
		return new HandshakeWebSocketService(requestUpgradeStrategy);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.WebsocketRouting.enabled",
			matchIfMissing = true)
	public WebsocketRoutingFilter websocketRoutingFilter(WebSocketClient webSocketClient,
			WebSocketService webSocketService,
			ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
		return new WebsocketRoutingFilter(webSocketClient, webSocketService,
				headersFilters);
	}

	@Bean
	public WeightCalculatorWebFilter weightCalculatorWebFilter(
			ConfigurationService configurationService,
			ObjectProvider<RouteLocator> routeLocator) {
		return new WeightCalculatorWebFilter(routeLocator, configurationService);
	}

	/*
	 * @Bean //TODO: default over netty? configurable public WebClientHttpRoutingFilter
	 * webClientHttpRoutingFilter() { //TODO: WebClient bean return new
	 * WebClientHttpRoutingFilter(WebClient.routes().build()); }
	 *
	 * @Bean public WebClientWriteResponseFilter webClientWriteResponseFilter() { return
	 * new WebClientWriteResponseFilter(); }
	 */

	// Predicate Factory beans

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.AfterRoute.enabled",
			matchIfMissing = true)
	public AfterRoutePredicateFactory afterRoutePredicateFactory() {
		return new AfterRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.BeforeRoute.enabled",
			matchIfMissing = true)
	public BeforeRoutePredicateFactory beforeRoutePredicateFactory() {
		return new BeforeRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.BetweenRoute.enabled",
			matchIfMissing = true)
	public BetweenRoutePredicateFactory betweenRoutePredicateFactory() {
		return new BetweenRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.CookieRoute.enabled",
			matchIfMissing = true)
	public CookieRoutePredicateFactory cookieRoutePredicateFactory() {
		return new CookieRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.HeaderRoute.enabled",
			matchIfMissing = true)
	public HeaderRoutePredicateFactory headerRoutePredicateFactory() {
		return new HeaderRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.HostRoute.enabled",
			matchIfMissing = true)
	public HostRoutePredicateFactory hostRoutePredicateFactory() {
		return new HostRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.MethodRoute.enabled",
			matchIfMissing = true)
	public MethodRoutePredicateFactory methodRoutePredicateFactory() {
		return new MethodRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.PathRoute.enabled",
			matchIfMissing = true)
	public PathRoutePredicateFactory pathRoutePredicateFactory() {
		return new PathRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.QueryRoute.enabled",
			matchIfMissing = true)
	public QueryRoutePredicateFactory queryRoutePredicateFactory() {
		return new QueryRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.ReadBody.enabled",
			matchIfMissing = true)
	public ReadBodyPredicateFactory readBodyPredicateFactory(
			ServerCodecConfigurer codecConfigurer) {
		return new ReadBodyPredicateFactory(codecConfigurer.getReaders());
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RemoteAddrRoute.enabled",
			matchIfMissing = true)
	public RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory() {
		return new RemoteAddrRoutePredicateFactory();
	}

	@Bean
	@DependsOn("weightCalculatorWebFilter")
	@ConditionalOnProperty(name = "spring.cloud.gateway.WeightRoute.enabled",
			matchIfMissing = true)
	public WeightRoutePredicateFactory weightRoutePredicateFactory() {
		return new WeightRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.CloudFoundryRoute.enabled",
			matchIfMissing = true)
	public CloudFoundryRouteServiceRoutePredicateFactory cloudFoundryRouteServiceRoutePredicateFactory() {
		return new CloudFoundryRouteServiceRoutePredicateFactory();
	}

	// GatewayFilter Factory beans

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.AddRequestHeader.enabled",
			matchIfMissing = true)
	public AddRequestHeaderGatewayFilterFactory addRequestHeaderGatewayFilterFactory() {
		return new AddRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.MapRequestHeader.enabled",
			matchIfMissing = true)
	public MapRequestHeaderGatewayFilterFactory mapRequestHeaderGatewayFilterFactory() {
		return new MapRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.AddRequestParameter.enabled",
			matchIfMissing = true)
	public AddRequestParameterGatewayFilterFactory addRequestParameterGatewayFilterFactory() {
		return new AddRequestParameterGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.AddResponseHeader.enabled",
			matchIfMissing = true)
	public AddResponseHeaderGatewayFilterFactory addResponseHeaderGatewayFilterFactory() {
		return new AddResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.ModifyRequestBody.enabled",
			matchIfMissing = true)
	public ModifyRequestBodyGatewayFilterFactory modifyRequestBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer) {
		return new ModifyRequestBodyGatewayFilterFactory(codecConfigurer.getReaders());
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.DedupeResponseHeader.enabled",
			matchIfMissing = true)
	public DedupeResponseHeaderGatewayFilterFactory dedupeResponseHeaderGatewayFilterFactory() {
		return new DedupeResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.ModifyResponseBody.enabled",
			matchIfMissing = true)
	public ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer, Set<MessageBodyDecoder> bodyDecoders,
			Set<MessageBodyEncoder> bodyEncoders) {
		return new ModifyResponseBodyGatewayFilterFactory(codecConfigurer.getReaders(),
				bodyDecoders, bodyEncoders);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.PrefixPath.enabled",
			matchIfMissing = true)
	public PrefixPathGatewayFilterFactory prefixPathGatewayFilterFactory() {
		return new PrefixPathGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.PreserveHostHeader.enabled",
			matchIfMissing = true)
	public PreserveHostHeaderGatewayFilterFactory preserveHostHeaderGatewayFilterFactory() {
		return new PreserveHostHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RedirectTo.enabled",
			matchIfMissing = true)
	public RedirectToGatewayFilterFactory redirectToGatewayFilterFactory() {
		return new RedirectToGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RemoveRequestHeader.enabled",
			matchIfMissing = true)
	public RemoveRequestHeaderGatewayFilterFactory removeRequestHeaderGatewayFilterFactory() {
		return new RemoveRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RemoveRequestParameter.enabled",
			matchIfMissing = true)
	public RemoveRequestParameterGatewayFilterFactory removeRequestParameterGatewayFilterFactory() {
		return new RemoveRequestParameterGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RemoveResponseHeader.enabled",
			matchIfMissing = true)
	public RemoveResponseHeaderGatewayFilterFactory removeResponseHeaderGatewayFilterFactory() {
		return new RemoveResponseHeaderGatewayFilterFactory();
	}

	@Bean(name = PrincipalNameKeyResolver.BEAN_NAME)
	@ConditionalOnBean(RateLimiter.class)
	@ConditionalOnMissingBean(KeyResolver.class)
	public PrincipalNameKeyResolver principalNameKeyResolver() {
		return new PrincipalNameKeyResolver();
	}

	@Bean
	@ConditionalOnBean({ RateLimiter.class, KeyResolver.class })
	@ConditionalOnProperty(name = "spring.cloud.gateway.RequestRateLimiter.enabled",
			matchIfMissing = true)
	public RequestRateLimiterGatewayFilterFactory requestRateLimiterGatewayFilterFactory(
			RateLimiter rateLimiter, KeyResolver resolver) {
		return new RequestRateLimiterGatewayFilterFactory(rateLimiter, resolver);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RewritePath.enabled",
			matchIfMissing = true)
	public RewritePathGatewayFilterFactory rewritePathGatewayFilterFactory() {
		return new RewritePathGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.Retry.enabled",
			matchIfMissing = true)
	public RetryGatewayFilterFactory retryGatewayFilterFactory() {
		return new RetryGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SetPath.enabled",
			matchIfMissing = true)
	public SetPathGatewayFilterFactory setPathGatewayFilterFactory() {
		return new SetPathGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SecureHeaders.enabled",
			matchIfMissing = true)
	public SecureHeadersGatewayFilterFactory secureHeadersGatewayFilterFactory(
			SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SetRequestHeader.enabled",
			matchIfMissing = true)
	public SetRequestHeaderGatewayFilterFactory setRequestHeaderGatewayFilterFactory() {
		return new SetRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SetRequestHostHeader.enabled",
			matchIfMissing = true)
	public SetRequestHostHeaderGatewayFilterFactory setRequestHostHeaderGatewayFilterFactory() {
		return new SetRequestHostHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SetResponseHeader.enabled",
			matchIfMissing = true)
	public SetResponseHeaderGatewayFilterFactory setResponseHeaderGatewayFilterFactory() {
		return new SetResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RewriteResponseHeader.enabled",
			matchIfMissing = true)
	public RewriteResponseHeaderGatewayFilterFactory rewriteResponseHeaderGatewayFilterFactory() {
		return new RewriteResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RewriteLocationResponseHeader.enabled",
			matchIfMissing = true)
	public RewriteLocationResponseHeaderGatewayFilterFactory rewriteLocationResponseHeaderGatewayFilterFactory() {
		return new RewriteLocationResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SetStatus.enabled",
			matchIfMissing = true)
	public SetStatusGatewayFilterFactory setStatusGatewayFilterFactory() {
		return new SetStatusGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.SaveSession.enabled",
			matchIfMissing = true)
	public SaveSessionGatewayFilterFactory saveSessionGatewayFilterFactory() {
		return new SaveSessionGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.StripPrefix.enabled",
			matchIfMissing = true)
	public StripPrefixGatewayFilterFactory stripPrefixGatewayFilterFactory() {
		return new StripPrefixGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RequestHeaderToRequestUri.enabled",
			matchIfMissing = true)
	public RequestHeaderToRequestUriGatewayFilterFactory requestHeaderToRequestUriGatewayFilterFactory() {
		return new RequestHeaderToRequestUriGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RequestSize.enabled",
			matchIfMissing = true)
	public RequestSizeGatewayFilterFactory requestSizeGatewayFilterFactory() {
		return new RequestSizeGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.RequestHeaderSize.enabled",
			matchIfMissing = true)
	public RequestHeaderSizeGatewayFilterFactory requestHeaderSizeGatewayFilterFactory() {
		return new RequestHeaderSizeGatewayFilterFactory();
	}

	@Bean
	public GzipMessageBodyResolver gzipMessageBodyResolver() {
		return new GzipMessageBodyResolver();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpClient.class)
	protected static class NettyConfiguration {

		protected final Log logger = LogFactory.getLog(getClass());

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.httpserver.wiretap")
		public NettyWebServerFactoryCustomizer nettyServerWiretapCustomizer(
				Environment environment, ServerProperties serverProperties) {
			return new NettyWebServerFactoryCustomizer(environment, serverProperties) {
				@Override
				public void customize(NettyReactiveWebServerFactory factory) {
					factory.addServerCustomizers(httpServer -> httpServer.wiretap(true));
					super.customize(factory);
				}
			};
		}

		@Bean
		@ConditionalOnMissingBean
		public HttpClient gatewayHttpClient(HttpClientProperties properties,
				List<HttpClientCustomizer> customizers) {

			// configure pool resources
			HttpClientProperties.Pool pool = properties.getPool();

			ConnectionProvider connectionProvider;
			if (pool.getType() == DISABLED) {
				connectionProvider = ConnectionProvider.newConnection();
			}
			else if (pool.getType() == FIXED) {
				connectionProvider = ConnectionProvider.fixed(pool.getName(),
						pool.getMaxConnections(), pool.getAcquireTimeout(),
						pool.getMaxIdleTime(), pool.getMaxLifeTime());
			}
			else {
				connectionProvider = ConnectionProvider.elastic(pool.getName(),
						pool.getMaxIdleTime(), pool.getMaxLifeTime());
			}

			HttpClient httpClient = HttpClient.create(connectionProvider)
					// TODO: move customizations to HttpClientCustomizers
					.httpResponseDecoder(spec -> {
						if (properties.getMaxHeaderSize() != null) {
							// cast to int is ok, since @Max is Integer.MAX_VALUE
							spec.maxHeaderSize(
									(int) properties.getMaxHeaderSize().toBytes());
						}
						if (properties.getMaxInitialLineLength() != null) {
							// cast to int is ok, since @Max is Integer.MAX_VALUE
							spec.maxInitialLineLength(
									(int) properties.getMaxInitialLineLength().toBytes());
						}
						return spec;
					}).tcpConfiguration(tcpClient -> {

						if (properties.getConnectTimeout() != null) {
							tcpClient = tcpClient.option(
									ChannelOption.CONNECT_TIMEOUT_MILLIS,
									properties.getConnectTimeout());
						}

						// configure proxy if proxy host is set.
						HttpClientProperties.Proxy proxy = properties.getProxy();

						if (StringUtils.hasText(proxy.getHost())) {

							tcpClient = tcpClient.proxy(proxySpec -> {
								ProxyProvider.Builder builder = proxySpec
										.type(ProxyProvider.Proxy.HTTP)
										.host(proxy.getHost());

								PropertyMapper map = PropertyMapper.get();

								map.from(proxy::getPort).whenNonNull().to(builder::port);
								map.from(proxy::getUsername).whenHasText()
										.to(builder::username);
								map.from(proxy::getPassword).whenHasText()
										.to(password -> builder.password(s -> password));
								map.from(proxy::getNonProxyHostsPattern).whenHasText()
										.to(builder::nonProxyHosts);
							});
						}
						return tcpClient;
					});

			HttpClientProperties.Ssl ssl = properties.getSsl();
			if ((ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0)
					|| ssl.getTrustedX509CertificatesForTrustManager().length > 0
					|| ssl.isUseInsecureTrustManager()) {
				httpClient = httpClient.secure(sslContextSpec -> {
					// configure ssl
					SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

					X509Certificate[] trustedX509Certificates = ssl
							.getTrustedX509CertificatesForTrustManager();
					if (trustedX509Certificates.length > 0) {
						sslContextBuilder = sslContextBuilder
								.trustManager(trustedX509Certificates);
					}
					else if (ssl.isUseInsecureTrustManager()) {
						sslContextBuilder = sslContextBuilder
								.trustManager(InsecureTrustManagerFactory.INSTANCE);
					}

					try {
						sslContextBuilder = sslContextBuilder
								.keyManager(ssl.getKeyManagerFactory());
					}
					catch (Exception e) {
						logger.error(e);
					}

					sslContextSpec.sslContext(sslContextBuilder)
							.defaultConfiguration(ssl.getDefaultConfigurationType())
							.handshakeTimeout(ssl.getHandshakeTimeout())
							.closeNotifyFlushTimeout(ssl.getCloseNotifyFlushTimeout())
							.closeNotifyReadTimeout(ssl.getCloseNotifyReadTimeout());
				});
			}

			if (properties.isWiretap()) {
				httpClient = httpClient.wiretap(true);
			}

			if (!CollectionUtils.isEmpty(customizers)) {
				customizers.sort(AnnotationAwareOrderComparator.INSTANCE);
				for (HttpClientCustomizer customizer : customizers) {
					httpClient = customizer.customize(httpClient);
				}
			}

			return httpClient;
		}

		@Bean
		public HttpClientProperties httpClientProperties() {
			return new HttpClientProperties();
		}

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.Netty.enabled",
				matchIfMissing = true)
		public NettyRoutingFilter routingFilter(HttpClient httpClient,
				ObjectProvider<List<HttpHeadersFilter>> headersFilters,
				HttpClientProperties properties) {
			return new NettyRoutingFilter(httpClient, headersFilters, properties);
		}

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.Netty.enabled",
				matchIfMissing = true)
		public NettyWriteResponseFilter nettyWriteResponseFilter(
				GatewayProperties properties) {
			return new NettyWriteResponseFilter(properties.getStreamingMediaTypes());
		}

		@Bean
		public ReactorNettyWebSocketClient reactorNettyWebSocketClient(
				HttpClientProperties properties, HttpClient httpClient) {
			ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient(
					httpClient);
			if (properties.getWebsocket().getMaxFramePayloadLength() != null) {
				webSocketClient.setMaxFramePayloadLength(
						properties.getWebsocket().getMaxFramePayloadLength());
			}
			webSocketClient.setHandlePing(properties.getWebsocket().isProxyPing());
			return webSocketClient;
		}

		@Bean
		public ReactorNettyRequestUpgradeStrategy reactorNettyRequestUpgradeStrategy(
				HttpClientProperties httpClientProperties) {
			ReactorNettyRequestUpgradeStrategy requestUpgradeStrategy = new ReactorNettyRequestUpgradeStrategy();

			HttpClientProperties.Websocket websocket = httpClientProperties
					.getWebsocket();
			PropertyMapper map = PropertyMapper.get();
			map.from(websocket::getMaxFramePayloadLength).whenNonNull()
					.to(requestUpgradeStrategy::setMaxFramePayloadLength);
			map.from(websocket::isProxyPing).to(requestUpgradeStrategy::setHandlePing);
			return requestUpgradeStrategy;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ HystrixObservableCommand.class, RxReactiveStreams.class })
	protected static class HystrixConfiguration {

		@Bean
		public HystrixGatewayFilterFactory hystrixGatewayFilterFactory(
				ObjectProvider<DispatcherHandler> dispatcherHandler) {
			return new HystrixGatewayFilterFactory(dispatcherHandler);
		}

		@Bean
		@ConditionalOnMissingBean(FallbackHeadersGatewayFilterFactory.class)
		public FallbackHeadersGatewayFilterFactory fallbackHeadersGatewayFilterFactory() {
			return new FallbackHeadersGatewayFilterFactory();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Health.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.actuator.verbose.enabled",
				matchIfMissing = true)
		@ConditionalOnAvailableEndpoint
		public GatewayControllerEndpoint gatewayControllerEndpoint(
				List<GlobalFilter> globalFilters,
				List<GatewayFilterFactory> gatewayFilters,
				List<RoutePredicateFactory> routePredicates,
				RouteDefinitionWriter routeDefinitionWriter, RouteLocator routeLocator) {
			return new GatewayControllerEndpoint(globalFilters, gatewayFilters,
					routePredicates, routeDefinitionWriter, routeLocator);
		}

		@Bean
		@Conditional(OnVerboseDisabledCondition.class)
		@ConditionalOnAvailableEndpoint
		public GatewayLegacyControllerEndpoint gatewayLegacyControllerEndpoint(
				RouteDefinitionLocator routeDefinitionLocator,
				List<GlobalFilter> globalFilters,
				List<GatewayFilterFactory> gatewayFilters,
				List<RoutePredicateFactory> routePredicates,
				RouteDefinitionWriter routeDefinitionWriter, RouteLocator routeLocator) {
			return new GatewayLegacyControllerEndpoint(routeDefinitionLocator,
					globalFilters, gatewayFilters, routePredicates, routeDefinitionWriter,
					routeLocator);
		}

	}

	private static class OnVerboseDisabledCondition extends NoneNestedConditions {

		OnVerboseDisabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = "spring.cloud.gateway.actuator.verbose.enabled",
				matchIfMissing = true)
		static class VerboseDisabled {

		}

	}

}
