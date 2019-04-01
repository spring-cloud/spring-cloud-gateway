package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Toshiaki Maki
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RequestHeaderToRequestUriGatewayFilterFactoryIntegrationTests
		extends BaseWebClientTests {
	@LocalServerPort
	int port;

	@Test
	@Ignore
	public void changeUriWorkWithProperties() {
		testClient.get().uri("/").header("Host", "www.changeuri.org")
				.header("X-CF-Forwarded-Url",
						"http://localhost:" + port + "/actuator/health")
				.exchange().expectBody(JsonNode.class)
				.consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@Test
	@Ignore
	public void changeUriWorkWithDsl() {
		testClient.get().uri("/").header("Host", "www.changeuri.org")
				.header("X-Next-Url", "http://localhost:" + port + "/actuator/health")
				.exchange().expectBody(JsonNode.class)
				.consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@Test
	public void changeUriWorkWithCustomLogic() {
		testClient.get()
				.uri(b -> b.path("/")
						.queryParam("url",
								"http://localhost:" + port + "/actuator/health")
						.build())
				.header("Host", "www.changeuri.org").exchange().expectBody(JsonNode.class)
				.consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {
		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route(r -> r.host("**.changeuri.org").and().header("X-Next-Url")
							.filters(f -> f.requestHeaderToRequestUri("X-Next-Url"))
							.uri("https://example.com"))
					.route(r -> r.host("**.changeuri.org").and().query("url")
							.filters(f -> f.changeRequestUri(e -> Optional.of(URI.create(
									e.getRequest().getQueryParams().getFirst("url")))))
							.uri("https://example.com"))
					.build();
		}
	}
}