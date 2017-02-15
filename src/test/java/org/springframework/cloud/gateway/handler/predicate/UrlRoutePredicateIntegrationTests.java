package org.springframework.cloud.gateway.handler.predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.assertStatus;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class UrlRoutePredicateIntegrationTests extends BaseWebClientTests {

	@Test
	public void urlRouteWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertStatus(response, HttpStatus.OK);
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(RoutePredicateHandlerMapping.class.getSimpleName());
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("default_path_to_httpbin");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig { }

}
