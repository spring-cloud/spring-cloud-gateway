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
import java.util.Map;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.api.RouteWriter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.WriteResponseFilter;
import org.springframework.cloud.gateway.filter.route.AddRequestHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.AddRequestParameterRouteFilter;
import org.springframework.cloud.gateway.filter.route.AddResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.HystrixRouteFilter;
import org.springframework.cloud.gateway.filter.route.RedirectToRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveNonProxyHeadersRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveRequestHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RewritePathRouteFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.filter.route.SecureHeadersProperties;
import org.springframework.cloud.gateway.filter.route.SecureHeadersRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetPathRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetStatusRouteFilter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.NettyRoutingWebHandler;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.UrlRoutePredicate;
import org.springframework.cloud.gateway.support.CachingRouteLocator;
import org.springframework.cloud.gateway.support.InMemoryRouteRepository;
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
	@ConditionalOnMissingBean(RouteLocator.class)
	public RouteLocator routeLocator(GatewayProperties properties) {
		//TODO: how to automatically apply CachingRouteLocator
		return new CachingRouteLocator(new PropertiesRouteLocator(properties));
	}

	@Bean
	public FilteringWebHandler filteringWebHandler(GatewayProperties properties, List<GlobalFilter> globalFilters,
												   Map<String, RouteFilter> routeFilters) {
		return new FilteringWebHandler(properties, globalFilters, routeFilters);
	}

	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
																	 Map<String, RoutePredicate> predicates,
																	 RouteLocator routeLocator) {
		return new RoutePredicateHandlerMapping(webHandler, predicates, routeLocator);
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

	// Predicate beans

	@Bean(name = "AfterRoutePredicate")
	public AfterRoutePredicate afterRoutePredicate() {
		return new AfterRoutePredicate();
	}

	@Bean(name = "BeforeRoutePredicate")
	public BeforeRoutePredicate beforeRoutePredicate() {
		return new BeforeRoutePredicate();
	}

	@Bean(name = "BetweenRoutePredicate")
	public BetweenRoutePredicate betweenRoutePredicate() {
		return new BetweenRoutePredicate();
	}

	@Bean(name = "CookieRoutePredicate")
	public CookieRoutePredicate cookieRoutePredicate() {
		return new CookieRoutePredicate();
	}

	@Bean(name = "HeaderRoutePredicate")
	public HeaderRoutePredicate headerRoutePredicate() {
		return new HeaderRoutePredicate();
	}

	@Bean(name = "HostRoutePredicate")
	public HostRoutePredicate hostRoutePredicate() {
		return new HostRoutePredicate();
	}

	@Bean(name = "MethodRoutePredicate")
	public MethodRoutePredicate methodRoutePredicate() {
		return new MethodRoutePredicate();
	}

	@Bean(name = "QueryRoutePredicate")
	public QueryRoutePredicate queryRoutePredicate() {
		return new QueryRoutePredicate();
	}

	@Bean(name = "RemoteAddrRoutePredicate")
	public RemoteAddrRoutePredicate remoteAddrRoutePredicate() {
		return new RemoteAddrRoutePredicate();
	}


	@Bean(name = "UrlRoutePredicate")
	public UrlRoutePredicate urlRoutePredicate() {
		return new UrlRoutePredicate();
	}

	// Filter Factory beans

	@Bean(name = "AddRequestHeaderRouteFilter")
	public AddRequestHeaderRouteFilter addRequestHeaderRouteFilter() {
		return new AddRequestHeaderRouteFilter();
	}

	@Bean(name = "AddRequestParameterRouteFilter")
	public AddRequestParameterRouteFilter addRequestParameterRouteFilter() {
		return new AddRequestParameterRouteFilter();
	}

	@Bean(name = "AddResponseHeaderRouteFilter")
	public AddResponseHeaderRouteFilter addResponseHeaderRouteFilter() {
		return new AddResponseHeaderRouteFilter();
	}

	@Configuration
	@ConditionalOnClass({HystrixObservableCommand.class, RxReactiveStreams.class})
	protected static class HystrixConfiguration {
		@Bean(name = "HystrixRouteFilter")
		public HystrixRouteFilter hystrixRouteFilter() {
			return new HystrixRouteFilter();
		}
	}

	@Bean(name = "RedirectToRouteFilter")
	public RedirectToRouteFilter redirectToRouteFilter() {
		return new RedirectToRouteFilter();
	}

	@Bean(name = "RemoveNonProxyHeadersRouteFilter")
	public RemoveNonProxyHeadersRouteFilter removeNonProxyHeadersRouteFilter() {
		return new RemoveNonProxyHeadersRouteFilter();
	}

	@Bean(name = "RemoveRequestHeaderRouteFilter")
	public RemoveRequestHeaderRouteFilter removeRequestHeaderRouteFilter() {
		return new RemoveRequestHeaderRouteFilter();
	}

	@Bean(name = "RemoveResponseHeaderRouteFilter")
	public RemoveResponseHeaderRouteFilter removeResponseHeaderRouteFilter() {
		return new RemoveResponseHeaderRouteFilter();
	}

	@Bean(name = "RewritePathRouteFilter")
	public RewritePathRouteFilter rewritePathRouteFilter() {
		return new RewritePathRouteFilter();
	}

	@Bean(name = "SetPathRouteFilter")
	public SetPathRouteFilter setPathRouteFilter() {
		return new SetPathRouteFilter();
	}

	@Bean(name = "SecureHeadersRouteFilter")
	public SecureHeadersRouteFilter secureHeadersRouteFilter(SecureHeadersProperties properties) {
		return new SecureHeadersRouteFilter(properties);
	}

	@Bean(name = "SetResponseHeaderRouteFilter")
	public SetResponseHeaderRouteFilter setResponseHeaderRouteFilter() {
		return new SetResponseHeaderRouteFilter();
	}

	@Bean(name = "SetStatusRouteFilter")
	public SetStatusRouteFilter setStatusRouteFilter() {
		return new SetStatusRouteFilter();
	}

	//TODO: control creation
	@Bean
	public InMemoryRouteRepository inMemoryRouteRepository() {
		return new InMemoryRouteRepository();
	}

	@Configuration
	@ConditionalOnClass(Endpoint.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayEndpoint gatewayEndpoint(RouteLocator routeLocator, List<GlobalFilter> globalFilters,
											   List<RouteFilter> routeFilters, FilteringWebHandler filteringWebHandler,
											   RouteWriter routeWriter) {
			return new GatewayEndpoint(routeLocator, globalFilters, routeFilters, filteringWebHandler, routeWriter);
		}
	}

}

