package org.springframework.cloud.gateway.handler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;


@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class RoutePredicateHandlerMappingWithAutoconfiguredClientIntegrationTests {
	@Autowired
	WebTestClient webTestClient;

	@BeforeAll
	static void setUp() {
		int managementPort = TestSocketUtils.findAvailableTcpPort();
		System.setProperty("management.server.port", String.valueOf(managementPort));
	}

	@Test
	void shouldReturnOk() {
		this.webTestClient.get().uri("/get")
						  .exchange()
						  .expectStatus()
						  .isOk();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	@RestController
	public static class TestConfig {
		@Value("${test.uri:http://httpbin.org:80}")
		String uri;

		@GetMapping("/get")
		String get() {
			return "hello";
		}
		@Bean
		RouteLocator testRoutes(RouteLocatorBuilder builder) {
			return builder
					.routes()
					.route(predicateSpec -> predicateSpec.path("/get").uri(uri))
					.build();
		}

	}
}