package org.springframework.cloud.gateway.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration
public class GatewayTestApplication {

	public static void main(String[] args) {
		System.setProperty("spring.cloud.bootstrap.enabled", "false"); //TODO: fix bootstrap
		SpringApplication.run(GatewayTestApplication.class, args);
	}
}
