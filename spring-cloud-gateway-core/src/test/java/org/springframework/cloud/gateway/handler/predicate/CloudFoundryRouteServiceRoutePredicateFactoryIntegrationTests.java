package org.springframework.cloud.gateway.handler.predicate;

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
public class CloudFoundryRouteServiceRoutePredicateFactoryIntegrationTests
		extends BaseWebClientTests {
	@LocalServerPort
	int port;

	@Test
	public void predicateWorkWithProperties() {
		testClient.get().uri("/").header("Host", "props.routeservice.example.com")
				.header("X-CF-Forwarded-Url",
						"http://localhost:" + port + "/actuator/health")
				.header("X-CF-Proxy-Signature", "foo")
				.header("X-CF-Proxy-Metadata", "bar").exchange()
				.expectBody(JsonNode.class)
				.consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@Test
	public void predicateWillNotWorkUnlessHeadersAreEnough() {
		testClient.get().uri("/").header("Host", "props.routeservice.example.com")
				.header("X-CF-Forwarded-Url",
						"http://localhost:" + port + "/actuator/health")
				.header("X-CF-Proxy-Metadata", "bar").exchange().expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
	}

	@Test
	public void predicateWorkWithDsl() {
		testClient.get().uri("/").header("Host", "dsl.routeservice.example.com")
				.header("X-CF-Forwarded-Url",
						"http://localhost:" + port + "/actuator/health")
				.header("X-CF-Proxy-Signature", "foo")
				.header("X-CF-Proxy-Metadata", "bar").exchange()
				.expectBody(JsonNode.class)
				.consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {
		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes().route(r -> r.cloudFoundryRouteService().and()
					.header("Host", "dsl.routeservice.example.com")
					.filters(f -> f.requestHeaderToRequestUri("X-CF-Forwarded-Url"))
					.uri("https://example.com")).build();
		}
	}
}