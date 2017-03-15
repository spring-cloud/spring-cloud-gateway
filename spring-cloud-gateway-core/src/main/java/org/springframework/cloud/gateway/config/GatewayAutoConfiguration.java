/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.config;

import java.util.List;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.api.RouteDefinitionLocator;
import org.springframework.cloud.gateway.api.RouteDefinitionWriter;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.WriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.HystrixWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveNonProxyHeadersWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.WebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersProperties;
import org.springframework.cloud.gateway.filter.factory.SetPathWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusWebFilterFactory;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.NettyRoutingWebHandler;
import org.springframework.cloud.gateway.handler.RequestPredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.AfterRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRequestPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RequestPredicateFactory;
import org.springframework.cloud.gateway.support.CachingRouteLocator;
import org.springframework.cloud.gateway.support.DefaultRouteLocator;
import org.springframework.cloud.gateway.support.InMemoryRouteDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.hystrix.HystrixObservableCommand;

import reactor.ipc.netty.http.client.HttpClient;
import rx.RxReactiveStreams;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnBean(GatewayConfiguration.Marker.class)
@EnableConfigurationProperties
public class GatewayAutoConfiguration {

	@Configuration
	@ConditionalOnClass(HttpClient.class)
	protected static class NettyConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public HttpClient httpClient() {
			return HttpClient.create(opts -> {
				//opts.poolResources(PoolResources.elastic("proxy"));
				//opts.disablePool(); //TODO: why do I need this again?
			});
		}

		@Bean
		public NettyRoutingWebHandler nettyRoutingWebHandler(HttpClient httpClient) {
			return new NettyRoutingWebHandler(httpClient);
		}

		@Bean
		public NettyRoutingFilter routingFilter(HttpClient httpClient) {
			return new NettyRoutingFilter(httpClient);
		}
	}

	@Bean
	// @ConditionalOnMissingBean(RouteDefinitionLocator.class)
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public RouteLocator defaultRouteLocator(GatewayProperties properties, List<GlobalFilter> globalFilters,
												   List<WebFilterFactory> webFilterFactories,
												   List<RequestPredicateFactory> predicates,
												   RouteDefinitionLocator routeDefinitionLocator) {
		return new CachingRouteLocator(new DefaultRouteLocator(properties, routeDefinitionLocator,
				predicates, globalFilters, webFilterFactories));
	}

	@Bean
	public FilteringWebHandler filteringWebHandler(GatewayProperties properties,
												   RouteLocator routeLocator) {
		return new FilteringWebHandler(properties, routeLocator);
	}

	@Bean
	public RequestPredicateHandlerMapping requestPredicateHandlerMapping(FilteringWebHandler webHandler,
																	   RouteLocator routeLocator) {
		return new RequestPredicateHandlerMapping(webHandler, routeLocator);
	}

	// ConfigurationProperty beans

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean
	public SecureHeadersProperties secureHeadersProperties() {
		return new SecureHeadersProperties();
	}

	// GlobalFilter beans

	@ConditionalOnClass(LoadBalancerClient.class)
	@Configuration
	protected static class LoadBalancerClientConfiguration {
		@Bean
		@ConditionalOnBean(LoadBalancerClient.class)
		public LoadBalancerClientFilter loadBalancerClientFilter(LoadBalancerClient client) {
			return new LoadBalancerClientFilter(client);
		}
	}

	@Bean
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean
	public WriteResponseFilter writeResponseFilter() {
		return new WriteResponseFilter();
	}

	// Request Predicate beans

	@Bean
	public AfterRequestPredicateFactory afterRequestPredicateFactory() {
		return new AfterRequestPredicateFactory();
	}

	@Bean
	public BeforeRequestPredicateFactory beforeRequestPredicateFactory() {
		return new BeforeRequestPredicateFactory();
	}

	@Bean
	public BetweenRequestPredicateFactory betweenRequestPredicateFactory() {
		return new BetweenRequestPredicateFactory();
	}

	@Bean
	public CookieRequestPredicateFactory cookieRequestPredicateFactory() {
		return new CookieRequestPredicateFactory();
	}

	@Bean
	public HeaderRequestPredicateFactory headerRequestPredicateFactory() {
		return new HeaderRequestPredicateFactory();
	}

	@Bean
	public HostRequestPredicateFactory hostRequestPredicateFactory() {
		return new HostRequestPredicateFactory();
	}

	@Bean
	public MethodRequestPredicateFactory methodRequestPredicateFactory() {
		return new MethodRequestPredicateFactory();
	}

	@Bean
	public PathRequestPredicateFactory pathRequestPredicateFactory() {
		return new PathRequestPredicateFactory();
	}

	@Bean
	public QueryRequestPredicateFactory queryRequestPredicateFactory() {
		return new QueryRequestPredicateFactory();
	}

	@Bean
	public RemoteAddrRequestPredicateFactory remoteAddrRequestPredicateFactory() {
		return new RemoteAddrRequestPredicateFactory();
	}

	// Filter Factory beans

	@Bean
	public AddRequestHeaderWebFilterFactory addRequestHeaderWebFilterFactory() {
		return new AddRequestHeaderWebFilterFactory();
	}

	@Bean
	public AddRequestParameterWebFilterFactory addRequestParameterWebFilterFactory() {
		return new AddRequestParameterWebFilterFactory();
	}

	@Bean
	public AddResponseHeaderWebFilterFactory addResponseHeaderWebFilterFactory() {
		return new AddResponseHeaderWebFilterFactory();
	}

	@Configuration
	@ConditionalOnClass({HystrixObservableCommand.class, RxReactiveStreams.class})
	protected static class HystrixConfiguration {
		@Bean
		public HystrixWebFilterFactory hystrixWebFilterFactory() {
			return new HystrixWebFilterFactory();
		}
	}

	@Bean
	public PrefixPathWebFilterFactory prefixPathWebFilterFactory() {
		return new PrefixPathWebFilterFactory();
	}

	@Bean
	public RedirectToWebFilterFactory redirectToWebFilterFactory() {
		return new RedirectToWebFilterFactory();
	}

	@Bean
	public RemoveNonProxyHeadersWebFilterFactory removeNonProxyHeadersWebFilterFactory() {
		return new RemoveNonProxyHeadersWebFilterFactory();
	}

	@Bean
	public RemoveRequestHeaderWebFilterFactory removeRequestHeaderWebFilterFactory() {
		return new RemoveRequestHeaderWebFilterFactory();
	}

	@Bean
	public RemoveResponseHeaderWebFilterFactory removeResponseHeaderWebFilterFactory() {
		return new RemoveResponseHeaderWebFilterFactory();
	}

	@Bean
	public RewritePathWebFilterFactory rewritePathWebFilterFactory() {
		return new RewritePathWebFilterFactory();
	}

	@Bean
	public SetPathWebFilterFactory setPathWebFilterFactory() {
		return new SetPathWebFilterFactory();
	}

	@Bean
	public SecureHeadersWebFilterFactory secureHeadersWebFilterFactory(SecureHeadersProperties properties) {
		return new SecureHeadersWebFilterFactory(properties);
	}

	@Bean
	public SetResponseHeaderWebFilterFactory setResponseHeaderWebFilterFactory() {
		return new SetResponseHeaderWebFilterFactory();
	}

	@Bean
	public SetStatusWebFilterFactory setStatusWebFilterFactory() {
		return new SetStatusWebFilterFactory();
	}

	//TODO: control creation
	@Bean
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}

	@Configuration
	@ConditionalOnClass(Endpoint.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayEndpoint gatewayEndpoint(RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
											   List<WebFilterFactory> webFilterFactories, RouteDefinitionWriter routeDefinitionWriter,
											   RouteLocator routeLocator) {
			return new GatewayEndpoint(routeDefinitionLocator, globalFilters, webFilterFactories, routeDefinitionWriter, routeLocator);
		}
	}

}

