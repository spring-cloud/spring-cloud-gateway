package org.springframework.cloud.gateway.config;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.handler.GatewayFilteringWebHandler;
import org.springframework.cloud.gateway.handler.HostPredicateFactory;
import org.springframework.cloud.gateway.handler.GatewayPredicateFactory;
import org.springframework.cloud.gateway.handler.GatewayWebHandler;
import org.springframework.cloud.gateway.handler.ServerWebExchangePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.UrlPredicateFactory;
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
	public ServerWebExchangePredicateHandlerMapping serverWebExchangePredicateHandlerMapping(GatewayProperties properties,
																							 GatewayFilteringWebHandler webHandler,
																							 List<GatewayPredicateFactory> predicateFactories) {
		return new ServerWebExchangePredicateHandlerMapping(webHandler, predicateFactories, properties);
	}

	@Bean
	public HostPredicateFactory hostPredicateFactory() {
		return new HostPredicateFactory();
	}

	@Bean
	public UrlPredicateFactory urlPredicateFactory() {
		return new UrlPredicateFactory();
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
