package org.springframework.cloud.gateway.handler.predicate;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.assertStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles({ "remote-address" })
public class RemoteAddrRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void remoteAddrWorks() {
		Mono<ClientResponse> result = webClient.get().uri("/ok/httpbin/").exchange();

		StepVerifier.create(result)
				.consumeNextWith(response -> assertStatus(response, HttpStatus.OK))
				.expectComplete().verify(DURATION);
	}

	@Test
	public void remoteAddrRejects() {
		Mono<ClientResponse> result = webClient.get().uri("/nok/httpbin/").exchange();

		StepVerifier
				.create(result)
				.consumeNextWith(response -> assertStatus(response, HttpStatus.NOT_FOUND))
				.expectComplete().verify(DURATION);
	}

	@Test
	public void remoteAddrWorksWithXForwardedRemoteAddress() {
		Mono<ClientResponse> result = webClient.get().uri("/xforwardfor")
				.header("X-Forwarded-For", "12.34.56.78").exchange();

		StepVerifier.create(result)
				.consumeNextWith(response -> assertStatus(response, HttpStatus.OK))
				.expectComplete().verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {
		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("x_forwarded_for_test", r -> r
					.path("/xforwardfor").and()
					.remoteAddr(XForwardedRemoteAddressResolver.maxTrustedIndex(1),
							"12.34.56.78")
					.filters(f -> f.setStatus(200)).uri(uri)).build();
		}
	}

}
