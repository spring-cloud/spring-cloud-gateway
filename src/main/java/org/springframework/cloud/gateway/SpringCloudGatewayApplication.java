package org.springframework.cloud.gateway;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.reactive.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class SpringCloudGatewayApplication {

	private static final Log log = LogFactory.getLog(SpringCloudGatewayApplication.class);

	@Bean
	public WebClient webClient() {
		return WebClient.builder(new ReactorClientHttpConnector()).build();
	}

	// TODO: request only, how to filter response?
	@Bean
	@Order(500)
	public WebFilter findRouteFilter() {
		return (exchange, chain) -> {
			log.info("findRoutFilter start");
			exchange.getAttributes().put("requestUrl", "http://httpbin.org/get");
			return chain.filter(exchange);
		};
	}

	@Bean
	@Order(501)
	public WebFilter modifyResponseFilter() {
		return (exchange, chain) -> {
			log.info("modifyResponseFilter start");
			exchange.getResponse().getHeaders().add("X-My-Custom", "MyCustomValue");
			return chain.filter(exchange);
		};
	}

	@Bean
	@Order(502)
	public WebFilter postFilter() {
		return (exchange, chain) -> {
			log.info("postFilter start");
			return chain.filter(exchange).then(postFilterWork(exchange));
		};
	}

	private static Mono<Void> postFilterWork(ServerWebExchange exchange) {
		log.info("postFilterWork");
		exchange.getResponse().getHeaders().add("X-Post-Header", "AddedAfterRoute");
		return Mono.empty();
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudGatewayApplication.class, args);
	}
}
