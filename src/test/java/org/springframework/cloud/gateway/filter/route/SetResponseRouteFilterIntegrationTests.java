package org.springframework.cloud.gateway.filter.route;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetResponseRouteFilterIntegrationTests extends BaseWebClientTests {

	@Test
	public void setResponseHeaderFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.setreresponseheader.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders).containsKey("X-Request-Foo");
							assertThat(httpHeaders.get("X-Request-Foo")).containsExactly("Bar");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig { }

}
