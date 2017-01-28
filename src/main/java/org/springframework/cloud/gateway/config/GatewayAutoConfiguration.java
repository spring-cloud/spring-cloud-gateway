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
import org.springframework.cloud.gateway.api.CachingRouteReader;
import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.RoutingFilter;
import org.springframework.cloud.gateway.filter.WriteResponseFilter;
import org.springframework.cloud.gateway.filter.route.AddRequestHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.AddRequestParameterRouteFilter;
import org.springframework.cloud.gateway.filter.route.AddResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.HystrixRouteFilter;
import org.springframework.cloud.gateway.filter.route.RedirectToRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveRequestHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RewritePathRouteFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.filter.route.SetPathRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetStatusRouteFilter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.RoutingWebHandler;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.UrlRoutePredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.hystrix.HystrixObservableCommand;

import reactor.ipc.netty.http.client.HttpClient;
import rx.RxReactiveStreams;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableConfigurationProperties
public class GatewayAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public HttpClient httpClient() {
		return HttpClient.create(opts -> {
			//opts.poolResources(PoolResources.elastic("proxy"));
			opts.disablePool();
		});
	}

	@Bean
	@ConditionalOnMissingBean(RouteReader.class)
	public RouteReader propertiesRouteReader(GatewayProperties properties) {
		//TODO: how to automatically apply CachingRouteReader
		return new CachingRouteReader(new PropertiesRouteReader(properties));
	}

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean
	public RoutingWebHandler routingWebHandler(HttpClient httpClient) {
		return new RoutingWebHandler(httpClient);
	}

	@Bean
	public FilteringWebHandler filteringWebHandler(//RoutingWebHandler webHandler,
												   List<GlobalFilter> globalFilters,
												   Map<String, RouteFilter> routeFilters) {
		return new FilteringWebHandler(/*webHandler,*/ globalFilters, routeFilters);
	}

	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
																	 Map<String, RoutePredicate> predicates,
																	 RouteReader routeReader) {
		return new RoutePredicateHandlerMapping(webHandler, predicates, routeReader);
	}

	// GlobalFilter beans

	@Bean
	@ConditionalOnClass(LoadBalancerClient.class)
	@ConditionalOnBean(LoadBalancerClient.class)
	public LoadBalancerClientFilter loadBalancerClientFilter(LoadBalancerClient client) {
		return new LoadBalancerClientFilter(client);
	}

	@Bean
	public RoutingFilter routingFilter(HttpClient httpClient) {
		return new RoutingFilter(httpClient);
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

	@Bean(name = "HystrixRouteFilter")
	@ConditionalOnClass({HystrixObservableCommand.class, RxReactiveStreams.class})
	public HystrixRouteFilter hystrixRouteFilter() {
		return new HystrixRouteFilter();
	}

	@Bean(name = "RedirectToRouteFilter")
	public RedirectToRouteFilter redirectToRouteFilter() {
		return new RedirectToRouteFilter();
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

	@Bean(name = "SetResponseHeaderRouteFilter")
	public SetResponseHeaderRouteFilter setResponseHeaderRouteFilter() {
		return new SetResponseHeaderRouteFilter();
	}

	@Bean(name = "SetStatusRouteFilter")
	public SetStatusRouteFilter setStatusRouteFilter() {
		return new SetStatusRouteFilter();
	}

	@Configuration
	@ConditionalOnClass(Endpoint.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayEndpoint gatewayEndpoint(RouteReader routeReader, List<GlobalFilter> globalFilters,
											   List<RouteFilter> routeFilters, FilteringWebHandler filteringWebHandler) {
			return new GatewayEndpoint(routeReader, globalFilters, routeFilters, filteringWebHandler);
		}
	}

}

