package org.springframework.cloud.gateway.filter.route;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.assertStatus;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetPathRouteFilterIntegrationTests extends BaseWebClientTests {

	@Test
	public void setPathFilterDefaultValuesWork() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/foo/get")
				.header("Host", "www.setpath.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertStatus(response, HttpStatus.OK);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig { }

}
