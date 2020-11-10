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
import java.time.Duration;
import java.util.List;
import java.util.Set;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.server.WebsocketServerSpec;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

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
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.NettyWebServerFactoryCustomizer;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.actuate.GatewayLegacyControllerEndpoint;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledGlobalFilter;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledPredicate;
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
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
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
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
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
import org.springframework.cloud.gateway.handler.predicate.ReadBodyRoutePredicateFactory;
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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
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
@AutoConfigureBefore({ HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class })
@AutoConfigureAfter({ GatewayReactiveLoadBalancerClientAutoConfiguration.class,
		GatewayClassPathWarningAutoConfiguration.class })
@ConditionalOnClass(DispatcherHandler.class)
public class GatewayAutoConfiguration {

	@Bean
	public StringToZonedDateTimeConverter stringToZonedDateTimeConverter() {
		return new StringToZonedDateTimeConverter();
	}

	@Bean
	public RouteLocatorBuilder routeLocatorBuilder(ConfigurableApplicationContext context) {
		return new RouteLocatorBuilder(context);
	}

	@Bean
	@ConditionalOnMissingBean
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	@Bean
	@ConditionalOnMissingBean(RouteDefinitionRepository.class)
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}

	@Bean
	@Primary
	public RouteDefinitionLocator routeDefinitionLocator(List<RouteDefinitionLocator> routeDefinitionLocators) {
		return new CompositeRouteDefinitionLocator(Flux.fromIterable(routeDefinitionLocators));
	}

	@Bean
	public ConfigurationService gatewayConfigurationService(BeanFactory beanFactory,
			@Qualifier("webFluxConversionService") ObjectProvider<ConversionService> conversionService,
			ObjectProvider<Validator> validator) {
		return new ConfigurationService(beanFactory, conversionService, validator);
	}

	@Bean
	public RouteLocator routeDefinitionRouteLocator(GatewayProperties properties,
			List<GatewayFilterFactory> gatewayFilters, List<RoutePredicateFactory> predicates,
			RouteDefinitionLocator routeDefinitionLocator, ConfigurationService configurationService) {
		return new RouteDefinitionRouteLocator(routeDefinitionLocator, predicates, gatewayFilters, properties,
				configurationService);
	}

	@Bean
	@Primary
	@ConditionalOnMissingBean(name = "cachedCompositeRouteLocator")
	// TODO: property to disable composite?
	public RouteLocator cachedCompositeRouteLocator(List<RouteLocator> routeLocators) {
		return new CachingRouteLocator(new CompositeRouteLocator(Flux.fromIterable(routeLocators)));
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.cloud.client.discovery.event.HeartbeatMonitor")
	public RouteRefreshListener routeRefreshListener(ApplicationEventPublisher publisher) {
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
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
			RouteLocator routeLocator, GlobalCorsProperties globalCorsProperties, Environment environment) {
		return new RoutePredicateHandlerMapping(webHandler, routeLocator, globalCorsProperties, environment);
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
	@ConditionalOnProperty(name = "spring.cloud.gateway.forwarded.enabled", matchIfMissing = true)
	public ForwardedHeadersFilter forwardedHeadersFilter() {
		return new ForwardedHeadersFilter();
	}

	// HttpHeaderFilter beans

	@Bean
	public RemoveHopByHopHeadersFilter removeHopByHopHeadersFilter() {
		return new RemoveHopByHopHeadersFilter();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.x-forwarded.enabled", matchIfMissing = true)
	public XForwardedHeadersFilter xForwardedHeadersFilter() {
		return new XForwardedHeadersFilter();
	}

	// GlobalFilter beans

	@Bean
	@ConditionalOnEnabledGlobalFilter
	public AdaptCachedBodyGlobalFilter adaptCachedBodyGlobalFilter() {
		return new AdaptCachedBodyGlobalFilter();
	}

	@Bean
	@ConditionalOnEnabledGlobalFilter
	public RemoveCachedBodyFilter removeCachedBodyFilter() {
		return new RemoveCachedBodyFilter();
	}

	@Bean
	@ConditionalOnEnabledGlobalFilter
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean
	@ConditionalOnEnabledGlobalFilter
	public ForwardRoutingFilter forwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new ForwardRoutingFilter(dispatcherHandler);
	}

	@Bean
	@ConditionalOnEnabledGlobalFilter
	public ForwardPathFilter forwardPathFilter() {
		return new ForwardPathFilter();
	}

	@Bean
	@ConditionalOnEnabledGlobalFilter(WebsocketRoutingFilter.class)
	public WebSocketService webSocketService(RequestUpgradeStrategy requestUpgradeStrategy) {
		return new HandshakeWebSocketService(requestUpgradeStrategy);
	}

	@Bean
	@ConditionalOnEnabledGlobalFilter
	public WebsocketRoutingFilter websocketRoutingFilter(WebSocketClient webSocketClient,
			WebSocketService webSocketService, ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
		return new WebsocketRoutingFilter(webSocketClient, webSocketService, headersFilters);
	}

	@Bean
	@ConditionalOnEnabledPredicate(WeightRoutePredicateFactory.class)
	public WeightCalculatorWebFilter weightCalculatorWebFilter(ConfigurationService configurationService,
			ObjectProvider<RouteLocator> routeLocator) {
		return new WeightCalculatorWebFilter(routeLocator, configurationService);
	}

	// Predicate Factory beans

	@Bean
	@ConditionalOnEnabledPredicate
	public AfterRoutePredicateFactory afterRoutePredicateFactory() {
		return new AfterRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public BeforeRoutePredicateFactory beforeRoutePredicateFactory() {
		return new BeforeRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public BetweenRoutePredicateFactory betweenRoutePredicateFactory() {
		return new BetweenRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public CookieRoutePredicateFactory cookieRoutePredicateFactory() {
		return new CookieRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public HeaderRoutePredicateFactory headerRoutePredicateFactory() {
		return new HeaderRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public HostRoutePredicateFactory hostRoutePredicateFactory() {
		return new HostRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public MethodRoutePredicateFactory methodRoutePredicateFactory() {
		return new MethodRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public PathRoutePredicateFactory pathRoutePredicateFactory() {
		return new PathRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public QueryRoutePredicateFactory queryRoutePredicateFactory() {
		return new QueryRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public ReadBodyRoutePredicateFactory readBodyPredicateFactory(ServerCodecConfigurer codecConfigurer) {
		return new ReadBodyRoutePredicateFactory(codecConfigurer.getReaders());
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory() {
		return new RemoteAddrRoutePredicateFactory();
	}

	@Bean
	@DependsOn("weightCalculatorWebFilter")
	@ConditionalOnEnabledPredicate
	public WeightRoutePredicateFactory weightRoutePredicateFactory() {
		return new WeightRoutePredicateFactory();
	}

	@Bean
	@ConditionalOnEnabledPredicate
	public CloudFoundryRouteServiceRoutePredicateFactory cloudFoundryRouteServiceRoutePredicateFactory() {
		return new CloudFoundryRouteServiceRoutePredicateFactory();
	}

	// GatewayFilter Factory beans

	@Bean
	@ConditionalOnEnabledFilter
	public AddRequestHeaderGatewayFilterFactory addRequestHeaderGatewayFilterFactory() {
		return new AddRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public MapRequestHeaderGatewayFilterFactory mapRequestHeaderGatewayFilterFactory() {
		return new MapRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public AddRequestParameterGatewayFilterFactory addRequestParameterGatewayFilterFactory() {
		return new AddRequestParameterGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public AddResponseHeaderGatewayFilterFactory addResponseHeaderGatewayFilterFactory() {
		return new AddResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public ModifyRequestBodyGatewayFilterFactory modifyRequestBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer) {
		return new ModifyRequestBodyGatewayFilterFactory(codecConfigurer.getReaders());
	}

	@Bean
	@ConditionalOnEnabledFilter
	public DedupeResponseHeaderGatewayFilterFactory dedupeResponseHeaderGatewayFilterFactory() {
		return new DedupeResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer, Set<MessageBodyDecoder> bodyDecoders,
			Set<MessageBodyEncoder> bodyEncoders) {
		return new ModifyResponseBodyGatewayFilterFactory(codecConfigurer.getReaders(), bodyDecoders, bodyEncoders);
	}

	@Bean
	@ConditionalOnEnabledFilter
	public PrefixPathGatewayFilterFactory prefixPathGatewayFilterFactory() {
		return new PrefixPathGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public PreserveHostHeaderGatewayFilterFactory preserveHostHeaderGatewayFilterFactory() {
		return new PreserveHostHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RedirectToGatewayFilterFactory redirectToGatewayFilterFactory() {
		return new RedirectToGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RemoveRequestHeaderGatewayFilterFactory removeRequestHeaderGatewayFilterFactory() {
		return new RemoveRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RemoveRequestParameterGatewayFilterFactory removeRequestParameterGatewayFilterFactory() {
		return new RemoveRequestParameterGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RemoveResponseHeaderGatewayFilterFactory removeResponseHeaderGatewayFilterFactory() {
		return new RemoveResponseHeaderGatewayFilterFactory();
	}

	@Bean(name = PrincipalNameKeyResolver.BEAN_NAME)
	@ConditionalOnBean(RateLimiter.class)
	@ConditionalOnMissingBean(KeyResolver.class)
	@ConditionalOnEnabledFilter(RequestRateLimiterGatewayFilterFactory.class)
	public PrincipalNameKeyResolver principalNameKeyResolver() {
		return new PrincipalNameKeyResolver();
	}

	@Bean
	@ConditionalOnBean({ RateLimiter.class, KeyResolver.class })
	@ConditionalOnEnabledFilter
	public RequestRateLimiterGatewayFilterFactory requestRateLimiterGatewayFilterFactory(RateLimiter rateLimiter,
			KeyResolver resolver) {
		return new RequestRateLimiterGatewayFilterFactory(rateLimiter, resolver);
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RewritePathGatewayFilterFactory rewritePathGatewayFilterFactory() {
		return new RewritePathGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RetryGatewayFilterFactory retryGatewayFilterFactory() {
		return new RetryGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SetPathGatewayFilterFactory setPathGatewayFilterFactory() {
		return new SetPathGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SecureHeadersGatewayFilterFactory secureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties);
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SetRequestHeaderGatewayFilterFactory setRequestHeaderGatewayFilterFactory() {
		return new SetRequestHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SetRequestHostHeaderGatewayFilterFactory setRequestHostHeaderGatewayFilterFactory() {
		return new SetRequestHostHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SetResponseHeaderGatewayFilterFactory setResponseHeaderGatewayFilterFactory() {
		return new SetResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RewriteResponseHeaderGatewayFilterFactory rewriteResponseHeaderGatewayFilterFactory() {
		return new RewriteResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RewriteLocationResponseHeaderGatewayFilterFactory rewriteLocationResponseHeaderGatewayFilterFactory() {
		return new RewriteLocationResponseHeaderGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SetStatusGatewayFilterFactory setStatusGatewayFilterFactory() {
		return new SetStatusGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public SaveSessionGatewayFilterFactory saveSessionGatewayFilterFactory() {
		return new SaveSessionGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public StripPrefixGatewayFilterFactory stripPrefixGatewayFilterFactory() {
		return new StripPrefixGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RequestHeaderToRequestUriGatewayFilterFactory requestHeaderToRequestUriGatewayFilterFactory() {
		return new RequestHeaderToRequestUriGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
	public RequestSizeGatewayFilterFactory requestSizeGatewayFilterFactory() {
		return new RequestSizeGatewayFilterFactory();
	}

	@Bean
	@ConditionalOnEnabledFilter
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
		public NettyWebServerFactoryCustomizer nettyServerWiretapCustomizer(Environment environment,
				ServerProperties serverProperties) {
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
		public HttpClient gatewayHttpClient(HttpClientProperties properties, List<HttpClientCustomizer> customizers) {

			// configure pool resources
			HttpClientProperties.Pool pool = properties.getPool();

			ConnectionProvider connectionProvider;
			if (pool.getType() == DISABLED) {
				connectionProvider = ConnectionProvider.newConnection();
			}
			else if (pool.getType() == FIXED) {
				ConnectionProvider.Builder builder = ConnectionProvider.builder(pool.getName())
						.maxConnections(pool.getMaxConnections()).pendingAcquireMaxCount(-1)
						.pendingAcquireTimeout(Duration.ofMillis(pool.getAcquireTimeout()));
				if (pool.getMaxIdleTime() != null) {
					builder.maxIdleTime(pool.getMaxIdleTime());
				}
				if (pool.getMaxLifeTime() != null) {
					builder.maxLifeTime(pool.getMaxLifeTime());
				}
				connectionProvider = builder.build();
			}
			else {
				ConnectionProvider.Builder builder = ConnectionProvider.builder(pool.getName())
						.maxConnections(Integer.MAX_VALUE).pendingAcquireTimeout(Duration.ofMillis(0))
						.pendingAcquireMaxCount(-1);
				if (pool.getMaxIdleTime() != null) {
					builder.maxIdleTime(pool.getMaxIdleTime());
				}
				if (pool.getMaxLifeTime() != null) {
					builder.maxLifeTime(pool.getMaxLifeTime());
				}
				connectionProvider = builder.build();
			}

			HttpClient httpClient = HttpClient.create(connectionProvider)
					// TODO: move customizations to HttpClientCustomizers
					.httpResponseDecoder(spec -> {
						if (properties.getMaxHeaderSize() != null) {
							// cast to int is ok, since @Max is Integer.MAX_VALUE
							spec.maxHeaderSize((int) properties.getMaxHeaderSize().toBytes());
						}
						if (properties.getMaxInitialLineLength() != null) {
							// cast to int is ok, since @Max is Integer.MAX_VALUE
							spec.maxInitialLineLength((int) properties.getMaxInitialLineLength().toBytes());
						}
						return spec;
					}).tcpConfiguration(tcpClient -> {

						if (properties.getConnectTimeout() != null) {
							tcpClient = tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
									properties.getConnectTimeout());
						}

						// configure proxy if proxy host is set.
						HttpClientProperties.Proxy proxy = properties.getProxy();

						if (StringUtils.hasText(proxy.getHost())) {

							tcpClient = tcpClient.proxy(proxySpec -> {
								ProxyProvider.Builder builder = proxySpec.type(ProxyProvider.Proxy.HTTP)
										.host(proxy.getHost());

								PropertyMapper map = PropertyMapper.get();

								map.from(proxy::getPort).whenNonNull().to(builder::port);
								map.from(proxy::getUsername).whenHasText().to(builder::username);
								map.from(proxy::getPassword).whenHasText()
										.to(password -> builder.password(s -> password));
								map.from(proxy::getNonProxyHostsPattern).whenHasText().to(builder::nonProxyHosts);
							});
						}
						return tcpClient;
					});

			HttpClientProperties.Ssl ssl = properties.getSsl();
			if ((ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0)
					|| ssl.getTrustedX509CertificatesForTrustManager().length > 0 || ssl.isUseInsecureTrustManager()) {
				httpClient = httpClient.secure(sslContextSpec -> {
					// configure ssl
					SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

					X509Certificate[] trustedX509Certificates = ssl.getTrustedX509CertificatesForTrustManager();
					if (trustedX509Certificates.length > 0) {
						sslContextBuilder = sslContextBuilder.trustManager(trustedX509Certificates);
					}
					else if (ssl.isUseInsecureTrustManager()) {
						sslContextBuilder = sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
					}

					try {
						sslContextBuilder = sslContextBuilder.keyManager(ssl.getKeyManagerFactory());
					}
					catch (Exception e) {
						logger.error(e);
					}

					sslContextSpec.sslContext(sslContextBuilder).defaultConfiguration(ssl.getDefaultConfigurationType())
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
		@ConditionalOnEnabledGlobalFilter
		public NettyRoutingFilter routingFilter(HttpClient httpClient,
				ObjectProvider<List<HttpHeadersFilter>> headersFilters, HttpClientProperties properties) {
			return new NettyRoutingFilter(httpClient, headersFilters, properties);
		}

		@Bean
		@ConditionalOnEnabledGlobalFilter(NettyRoutingFilter.class)
		public NettyWriteResponseFilter nettyWriteResponseFilter(GatewayProperties properties) {
			return new NettyWriteResponseFilter(properties.getStreamingMediaTypes());
		}

		@Bean
		@ConditionalOnEnabledGlobalFilter(WebsocketRoutingFilter.class)
		public ReactorNettyWebSocketClient reactorNettyWebSocketClient(HttpClientProperties properties,
				HttpClient httpClient) {
			WebsocketClientSpec.Builder builder = WebsocketClientSpec.builder()
					.handlePing(properties.getWebsocket().isProxyPing());
			if (properties.getWebsocket().getMaxFramePayloadLength() != null) {
				builder.maxFramePayloadLength(properties.getWebsocket().getMaxFramePayloadLength());
			}
			return new ReactorNettyWebSocketClient(httpClient, builder);
		}

		@Bean
		@ConditionalOnEnabledGlobalFilter(WebsocketRoutingFilter.class)
		public ReactorNettyRequestUpgradeStrategy reactorNettyRequestUpgradeStrategy(
				HttpClientProperties httpClientProperties) {

			WebsocketServerSpec.Builder builder = WebsocketServerSpec.builder();
			HttpClientProperties.Websocket websocket = httpClientProperties.getWebsocket();
			PropertyMapper map = PropertyMapper.get();
			map.from(websocket::getMaxFramePayloadLength).whenNonNull().to(builder::maxFramePayloadLength);
			map.from(websocket::isProxyPing).to(builder::handlePing);

			return new ReactorNettyRequestUpgradeStrategy(builder);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Health.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.actuator.verbose.enabled", matchIfMissing = true)
		@ConditionalOnAvailableEndpoint
		public GatewayControllerEndpoint gatewayControllerEndpoint(List<GlobalFilter> globalFilters,
				List<GatewayFilterFactory> gatewayFilters, List<RoutePredicateFactory> routePredicates,
				RouteDefinitionWriter routeDefinitionWriter, RouteLocator routeLocator,
				RouteDefinitionLocator routeDefinitionLocator) {
			return new GatewayControllerEndpoint(globalFilters, gatewayFilters, routePredicates, routeDefinitionWriter,
					routeLocator, routeDefinitionLocator);
		}

		@Bean
		@Conditional(OnVerboseDisabledCondition.class)
		@ConditionalOnAvailableEndpoint
		public GatewayLegacyControllerEndpoint gatewayLegacyControllerEndpoint(
				RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
				List<GatewayFilterFactory> gatewayFilters, List<RoutePredicateFactory> routePredicates,
				RouteDefinitionWriter routeDefinitionWriter, RouteLocator routeLocator) {
			return new GatewayLegacyControllerEndpoint(routeDefinitionLocator, globalFilters, gatewayFilters,
					routePredicates, routeDefinitionWriter, routeLocator);
		}

	}

	private static class OnVerboseDisabledCondition extends NoneNestedConditions {

		OnVerboseDisabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = "spring.cloud.gateway.actuator.verbose.enabled", matchIfMissing = true)
		static class VerboseDisabled {

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
	@ConditionalOnClass({ OAuth2AuthorizedClient.class, SecurityWebFilterChain.class, SecurityProperties.class })
	@ConditionalOnEnabledFilter(TokenRelayGatewayFilterFactory.class)
	protected static class TokenRelayConfiguration {

		@Bean
		public TokenRelayGatewayFilterFactory tokenRelayGatewayFilterFactory(
				ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManager) {
			return new TokenRelayGatewayFilterFactory(clientManager);
		}

	}

}
