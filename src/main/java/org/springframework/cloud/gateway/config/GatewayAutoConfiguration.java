package org.springframework.cloud.gateway.config;

import java.util.List;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.definition.AppendRequestHeaderFilter;
import org.springframework.cloud.gateway.filter.definition.AppendResponseHeaderFilter;
import org.springframework.cloud.gateway.filter.definition.GatewayFilterDefinition;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.handler.GatewayFilteringWebHandler;
import org.springframework.cloud.gateway.handler.GatewayPredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.GatewayWebHandler;
import org.springframework.cloud.gateway.handler.predicate.CookiePredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.HeaderPredicate;
import org.springframework.cloud.gateway.handler.predicate.HostPredicate;
import org.springframework.cloud.gateway.handler.predicate.MethodPredicate;
import org.springframework.cloud.gateway.handler.predicate.QueryPredicate;
import org.springframework.cloud.gateway.handler.predicate.UrlPredicate;
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
	public RouteToRequestUrlFilter findRouteFilter() {
		return new RouteToRequestUrlFilter();
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
	public GatewayWebHandler gatewayController(WebClient webClient) {
		return new GatewayWebHandler(webClient);
	}

	@Bean
	public GatewayFilteringWebHandler gatewayFilteringWebHandler(GatewayWebHandler gatewayWebHandler,
																 List<GatewayFilter> filters,
																 List<GatewayFilterDefinition> filterDefinitions) {
		return new GatewayFilteringWebHandler(gatewayWebHandler, filters, filterDefinitions);
	}

	// Predicate beans

	@Bean
	public GatewayPredicateHandlerMapping gatewayPredicateHandlerMapping(GatewayFilteringWebHandler webHandler,
																		 List<GatewayPredicate> predicates,
																		 RouteReader routeReader) {
		return new GatewayPredicateHandlerMapping(webHandler, predicates, routeReader);
	}

	@Bean
	public CookiePredicate cookiePredicate() {
		return new CookiePredicate();
	}

	@Bean
	public HeaderPredicate headerPredicate() {
		return new HeaderPredicate();
	}

	@Bean
	public HostPredicate hostPredicate() {
		return new HostPredicate();
	}

	@Bean
	public MethodPredicate methodPredicate() {
		return new MethodPredicate();
	}

	@Bean
	public QueryPredicate queryPredicate() {
		return new QueryPredicate();
	}

	@Bean
	public UrlPredicate urlPredicate() {
		return new UrlPredicate();
	}

	// Filter beans

	@Bean
	public AppendRequestHeaderFilter appendRequestHeaderFilter() {
		return new AppendRequestHeaderFilter();
	}

	@Bean
	public AppendResponseHeaderFilter appendResponseHeaderFilter() {
		return new AppendResponseHeaderFilter();
	}

	@Configuration
	@ConditionalOnClass(Endpoint.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayEndpoint gatewayEndpoint(List<GatewayFilter> filters) {
			return new GatewayEndpoint(filters);
		}
	}

}
