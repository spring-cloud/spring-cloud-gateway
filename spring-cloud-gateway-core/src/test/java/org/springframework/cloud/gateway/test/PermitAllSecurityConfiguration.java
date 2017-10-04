package org.springframework.cloud.gateway.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.HttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class PermitAllSecurityConfiguration {
	@Bean
	SecurityWebFilterChain springWebFilterChain(HttpSecurity http) throws Exception {
		return http.authorizeExchange()
				.anyExchange().permitAll()
				.and()
				.build();
	}
}
