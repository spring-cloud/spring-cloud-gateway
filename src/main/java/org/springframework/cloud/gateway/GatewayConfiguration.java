package org.springframework.cloud.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filters.FindRouteFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.reactive.WebClient;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableConfigurationProperties
public class GatewayConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebClient webClient() {
		return WebClient.builder(new ReactorClientHttpConnector()).build();
	}

	@Bean
	public FindRouteFilter findRouteFilter(GatewayProperties properties) {
		return new FindRouteFilter(properties);
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
	public GatewayHandlerMapping gatewayHandlerMapping(GatewayProperties properties, GatewayWebHandler gatewayWebHandler) {
		return new GatewayHandlerMapping(properties, gatewayWebHandler);
	}

	/*@Bean
	public GatewayWebReactiveConfigurer gatewayWebReactiveConfiguration() {
		return new GatewayWebReactiveConfigurer();
	}

	public static class GatewayWebReactiveConfigurer implements WebReactiveConfigurer {
		@Override
		public void addResourceHandlers(ResourceHandlerRegistry registry) {
		}
	}*/
}
