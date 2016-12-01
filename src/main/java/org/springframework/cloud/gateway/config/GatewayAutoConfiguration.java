package org.springframework.cloud.gateway.config;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.handler.GatewayFilteringWebHandler;
import org.springframework.cloud.gateway.handler.predicate.CookiePredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.HeaderPredicate;
import org.springframework.cloud.gateway.handler.predicate.HostPredicate;
import org.springframework.cloud.gateway.handler.GatewayWebHandler;
import org.springframework.cloud.gateway.handler.GatewayPredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.MethodPredicate;
import org.springframework.cloud.gateway.handler.predicate.QueryPredicate;
import org.springframework.cloud.gateway.handler.predicate.UrlPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.reactive.WebClient;

import java.util.List;

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
	public RouteToRequestUrlFilter findRouteFilter(GatewayProperties properties) {
		return new RouteToRequestUrlFilter(properties);
	}

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean
	public GatewayWebHandler gatewayController(GatewayProperties properties, WebClient webClient) {
		return new GatewayWebHandler(properties, webClient);
	}

	@Bean
	public GatewayFilteringWebHandler gatewayFilteringWebHandler(GatewayWebHandler gatewayWebHandler, GatewayFilter[] filters) {
		return new GatewayFilteringWebHandler(gatewayWebHandler, filters);
	}

	@Bean
	public GatewayPredicateHandlerMapping gatewayPredicateHandlerMapping(GatewayProperties properties,
																		 GatewayFilteringWebHandler webHandler,
																		 List<GatewayPredicate> predicates) {
		return new GatewayPredicateHandlerMapping(webHandler, predicates, properties);
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

	@Configuration
	@ConditionalOnClass(Endpoint.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayEndpoint gatewayEndpoint(List<GatewayFilter> filters) {
			return new GatewayEndpoint(filters);
		}
	}

}
