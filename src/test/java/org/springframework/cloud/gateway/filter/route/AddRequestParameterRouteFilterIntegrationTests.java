package org.springframework.cloud.gateway.filter.route;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class AddRequestParameterRouteFilterIntegrationTests extends BaseWebClientTests {

	@Test
	public void addRequestParameterFilterWorksBlankQuery() {
		testRequestParameterFilter("");
	}

	@Test
	public void addRequestParameterFilterWorksNonBlankQuery() {
		testRequestParameterFilter("?baz=bam");
	}

	private void testRequestParameterFilter(String query) {
		Mono<Map> result = webClient.get()
				.uri("/get" + query)
				.header("Host", "www.addrequestparameter.org")
				.exchange()
				.then(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							Map<String, Object> args = getMap(response, "args");
							assertThat(args).containsEntry("foo", "bar");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig { }

}
