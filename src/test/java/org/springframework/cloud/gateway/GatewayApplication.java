package org.springframework.cloud.gateway;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootConfiguration
@EnableAutoConfiguration
public class GatewayApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder()
				.sources(GatewayApplication.class)
				.run(args);
	}
}
