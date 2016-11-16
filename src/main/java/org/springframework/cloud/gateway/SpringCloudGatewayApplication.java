package org.springframework.cloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.reactive.WebClient;
import org.springframework.web.server.WebFilter;

@SpringBootApplication
public class SpringCloudGatewayApplication {

	@Bean
	public WebClient webClient() {
		return WebClient.builder(new ReactorClientHttpConnector()).build();
	}

	// TODO: request only, how to filter response?
	@Bean
	@Order(500)
	public WebFilter findRouteFilter() {
		return (exchange, chain) -> {
			exchange.getAttributes().put("requestUrl", "http://httpbin.org/get");
			return chain.filter(exchange);
		};
	}

	@Bean
	@Order(501)
	public WebFilter modifyResponseFilter() {
		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().add("X-My-Custom", "MyCustomValue");
			return chain.filter(exchange);
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudGatewayApplication.class, args);
	}
}
