package org.springframework.cloud.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.ServerSentEventHttpMessageReader;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Custom configuration of encoders/decoders and readers/writers for rewrite body.
 * 
 * @author Anton Brok-Volchansky
 * 
 * @see org.springframework.web.reactive.config.WebFluxConfigurationSupport
 *
 */
@Configuration
@EnableWebFlux
public class WebFluxGatewayConfigurationSupport implements WebFluxConfigurer {

	@Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
		configurer.customCodecs().reader(new ServerSentEventHttpMessageReader(new Jackson2JsonDecoder()));
	}
}
