package org.springframework.cloud.gateway.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.support.CachingRouteLocator;
import org.springframework.cloud.gateway.support.CompositeRouteLocator;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.PropertiesRouteLocator;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteLocator;
import org.springframework.cloud.gateway.support.InMemoryRouteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import reactor.core.publisher.Flux;

@SpringBootConfiguration
@EnableAutoConfiguration
public class GatewayTestApplication {

	/*
	TO test run `spring cloud configserver eureka`,
	then run this app with `--spring.profiles.active=discovery`
	should be able to hit http://localhost:8008/configserver/foo/default a normal configserver api
	 */
	@Configuration
	@EnableDiscoveryClient
	@Profile("discovery")
	protected static class GatewayDiscoveryConfiguration {

		@Bean
		public DiscoveryClientRouteLocator discoveryClientRouteLocator(DiscoveryClient discoveryClient) {
			return new DiscoveryClientRouteLocator(discoveryClient);
		}

		@Bean
		public PropertiesRouteLocator propertiesRouteLocator(GatewayProperties properties) {
			return new PropertiesRouteLocator(properties);
		}

		@Bean
		@Primary
		public RouteLocator compositeRouteLocator(InMemoryRouteRepository inMemoryRouteRepository,
												  DiscoveryClientRouteLocator discoveryClientRouteLocator,
												  PropertiesRouteLocator propertiesRouteLocator) {
			final Flux<RouteLocator> flux = Flux.just(inMemoryRouteRepository, discoveryClientRouteLocator, propertiesRouteLocator);
			final CompositeRouteLocator composite = new CompositeRouteLocator(flux);
			return new CachingRouteLocator(composite);
		}
	}

	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv4Stack", "true"); //Remove when configurable
		SpringApplication.run(GatewayTestApplication.class, args);
	}
}
