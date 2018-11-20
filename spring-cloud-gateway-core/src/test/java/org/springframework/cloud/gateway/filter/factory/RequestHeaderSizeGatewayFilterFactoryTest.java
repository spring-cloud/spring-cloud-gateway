package org.springframework.cloud.gateway.filter.factory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Sakalya Deshpande
 */

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RequestHeaderSizeGatewayFilterFactoryTest extends BaseWebClientTests {

	private static final String responseMesssage = "Request Header/s size is larger than permissible limit. Request Header/s size is 73 B where permissible limit is 46 B";

	@Test
	public void setRequestSizeFilterWorks() {
		testClient.get().uri("/headers")
				.header("Host", "www.test.org")
				.header("HeaderName","Some Very Large Header Name")
				.exchange().expectStatus().isEqualTo(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE)
				.expectHeader().valueMatches("errorMessage", responseMesssage);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {
		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("test_request_header_size",
					r -> r.order(-1).host("**.test.org").filters(f -> f.setRequestHeaderSize(46L)).uri(uri))
					.build();
		}
	}
}