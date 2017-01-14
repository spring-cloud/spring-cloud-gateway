package org.springframework.cloud.gateway.config;

import java.util.List;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderFilterFactory;
import org.springframework.cloud.gateway.filter.factory.FilterFactory;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.handler.GatewayFilteringWebHandler;
import org.springframework.cloud.gateway.handler.GatewayPredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.GatewayWebHandler;
import org.springframework.cloud.gateway.handler.predicate.CookiePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.UrlPredicateFactory;
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
																 List<FilterFactory> filterDefinitions) {
		return new GatewayFilteringWebHandler(gatewayWebHandler, filters, filterDefinitions);
	}

	// Predicate beans

	@Bean
	public GatewayPredicateHandlerMapping gatewayPredicateHandlerMapping(GatewayFilteringWebHandler webHandler,
																		 List<PredicateFactory> predicates,
																		 RouteReader routeReader) {
		return new GatewayPredicateHandlerMapping(webHandler, predicates, routeReader);
	}

	@Bean
	public CookiePredicateFactory cookiePredicateFactory() {
		return new CookiePredicateFactory();
	}

	@Bean
	public HeaderPredicateFactory headerPredicateFactory() {
		return new HeaderPredicateFactory();
	}

	@Bean
	public HostPredicateFactory hostPredicateFactory() {
		return new HostPredicateFactory();
	}

	@Bean
	public MethodPredicateFactory methodPredicateFactory() {
		return new MethodPredicateFactory();
	}

	@Bean
	public QueryPredicateFactory queryPredicateFactory() {
		return new QueryPredicateFactory();
	}

	@Bean
	public UrlPredicateFactory urlPredicateFactory() {
		return new UrlPredicateFactory();
	}

	// Filter Factory beans

	@Bean
	public AddRequestHeaderFilterFactory addRequestHeaderFilterFactory() {
		return new AddRequestHeaderFilterFactory();
	}

	@Bean
	public AddResponseHeaderFilterFactory addResponseHeaderFilterFactory() {
		return new AddResponseHeaderFilterFactory();
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
