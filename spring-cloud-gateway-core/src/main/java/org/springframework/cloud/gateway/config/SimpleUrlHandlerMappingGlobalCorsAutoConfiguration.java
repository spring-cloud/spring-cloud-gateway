package org.springframework.cloud.gateway.config;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * This is useful for PreFlight CORS requests. We can add a "global" configuration here so
 * we don't have to modify existing predicates to allow the "options" HTTP method.
 * 
 */

@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.globalcors.addToSimpleUrlHanderMapping", matchIfMissing = true)
public class SimpleUrlHandlerMappingGlobalCorsAutoConfiguration {

	@Autowired
	private GlobalCorsProperties globalCorsProperties;

	@Autowired
	private SimpleUrlHandlerMapping simpleUrlHandlerMapping;

	@PostConstruct
	void config() {
		simpleUrlHandlerMapping
				.setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

}
