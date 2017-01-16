package org.springframework.cloud.gateway.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.WriteResponseFilter;
import org.springframework.cloud.gateway.filter.route.AddRequestHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.AddResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveRequestHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RemoveResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.RewritePathRouteFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.filter.route.SetPathRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetResponseHeaderRouteFilter;
import org.springframework.cloud.gateway.filter.route.SetStatusRouteFilter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.WebClientRoutingWebHandler;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicate;
import org.springframework.cloud.gateway.handler.predicate.UrlRoutePredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableConfigurationProperties
public class GatewayAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebClient webClient() {
		return WebClient.builder(new ReactorClientHttpConnector()).build();
	}

	@Bean
	@ConditionalOnMissingBean(RouteReader.class)
	public PropertiesRouteReader propertiesRouteReader(GatewayProperties properties) {
		return new PropertiesRouteReader(properties);
	}

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean
	public WebClientRoutingWebHandler webClientRoutingWebHandler(WebClient webClient) {
		return new WebClientRoutingWebHandler(webClient);
	}

	@Bean
	public FilteringWebHandler filteringWebHandler(WebClientRoutingWebHandler webHandler,
														  List<GlobalFilter> globalFilters,
														  Map<String, RouteFilter> routeFilters) {
		return new FilteringWebHandler(webHandler, globalFilters, routeFilters);
	}

	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
																	   Map<String, RoutePredicate> predicates,
																	   RouteReader routeReader) {
		return new RoutePredicateHandlerMapping(webHandler, predicates, routeReader);
	}

	// GlobalFilter beans

	@Bean
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean
	public WriteResponseFilter writeResponseFilter() {
		return new WriteResponseFilter();
	}

	// Predicate beans

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

	@Bean(name = "AddResponseHeaderRouteFilter")
	public AddResponseHeaderRouteFilter addResponseHeaderRouteFilter() {
		return new AddResponseHeaderRouteFilter();
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
		public GatewayEndpoint gatewayEndpoint(List<GlobalFilter> filters) {
			return new GatewayEndpoint(filters);
		}
	}

}
