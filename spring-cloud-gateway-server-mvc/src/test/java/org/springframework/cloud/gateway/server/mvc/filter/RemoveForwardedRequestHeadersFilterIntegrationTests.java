package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter.FORWARDED_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

/**
 * Integration tests making sure all forwarded and x-forwarded headers are removed if no
 * trusted proxies set.
 *
 * @author Spencer Gibb
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.server.webmvc.forwarded.enabled=true", "spring.cloud.gateway.server.webmvc.x-forwarded.enabled=true",
				"spring.cloud.gateway.server.webmvc.trusted-proxies=10.0.0.1",
				"logging.level.org.springframework.cloud.gateway.mvc.filter=TRACE" })
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
@DirtiesContext
public class RemoveForwardedRequestHeadersFilterIntegrationTests {

	@Autowired
	TestRestClient testClient;

	@Test
	public void forwardedHeadersRemoved() {
		testClient.get()
				.uri("/headers")
				.header(FORWARDED_HEADER, "for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89")
				.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
				.header(X_FORWARDED_HOST_HEADER, "example.com")
				.header(X_FORWARDED_PORT_HEADER, "443")
				.header(X_FORWARDED_PROTO_HEADER, "https")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(Map.class)
				.consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).doesNotContainKeys(FORWARDED_HEADER,
							X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
							X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
				});
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	public static class TestConfig {
		@Bean
		public RouterFunction<ServerResponse> weightLowRouterFunction() {
			// @formatter:off
			return route("remove_forwarded")
					.GET("/headers", http())
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Route", "remove_forwarded"))
					.build();
			// @formatter:on
		}
	}
}
