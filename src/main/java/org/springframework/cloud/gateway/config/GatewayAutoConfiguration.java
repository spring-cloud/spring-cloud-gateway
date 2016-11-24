package org.springframework.cloud.gateway.config;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToUrlFilter;
import org.springframework.cloud.gateway.handler.GatewayFilteringWebHandler;
import org.springframework.cloud.gateway.handler.GatewayHostHandlerMapping;
import org.springframework.cloud.gateway.handler.GatewayUrlHandlerMapping;
import org.springframework.cloud.gateway.handler.GatewayWebHandler;
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
	public RouteToUrlFilter findRouteFilter(GatewayProperties properties) {
		return new RouteToUrlFilter(properties);
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
	public GatewayUrlHandlerMapping gatewayUrlHandlerMapping(GatewayProperties properties, GatewayFilteringWebHandler webHandler) {
		return new GatewayUrlHandlerMapping(properties, webHandler);
	}

	@Bean
	public GatewayHostHandlerMapping gatewayHostHandlerMapping(GatewayProperties properties, GatewayFilteringWebHandler webHandler) {
		return new GatewayHostHandlerMapping(properties, webHandler);
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
