package org.springframework.cloud.gateway.test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.handler.GatewayHostHandlerMapping;
import org.springframework.cloud.gateway.handler.GatewayUrlHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.reactive.ClientResponse;
import org.springframework.web.client.reactive.WebClient;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.web.client.reactive.ClientRequest.GET;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class GatewayIntegrationTests {

	public static final String HANDLER_MAPPER_HEADER = "X-Gateway-Handler-Mapper-Class";

	@LocalServerPort
	private int port;

	private WebClient webClient = WebClient.builder(new ReactorClientHttpConnector()).build();

	@Test
	public void urlRouteWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/get").build()
		);

		StepVerifier
				.create(result.map(response -> response.headers().asHttpHeaders()))
				.consumeNextWith(
						httpHeaders -> {
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(GatewayUrlHandlerMapping.class.getSimpleName());
						})
				.expectComplete()
				.verify();
	}

	@Test
	public void hostRouteWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/get")
						.header("Host", "www.example.org")
						.build()
		);

		StepVerifier
				.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							HttpStatus statusCode = response.statusCode();
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(GatewayHostHandlerMapping.class.getSimpleName());
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify();
	}

	@SpringBootConfiguration
	public static class TestConfig {

		private static final Log log = LogFactory.getLog(TestConfig.class);

		@Bean
		@Order(501)
		public GatewayFilter modifyResponseFilter() {
			return (exchange, chain) -> {
				log.info("modifyResponseFilter start");
				String value = (String) exchange.getAttribute(GatewayFilter.GATEWAY_HANDLER_MAPPER_ATTR).orElse("N/A");
				exchange.getResponse().getHeaders().add(HANDLER_MAPPER_HEADER, value);
				return chain.filter(exchange);
			};
		}

		@Bean
		@Order(502)
		public GatewayFilter postFilter() {
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

	}

}
