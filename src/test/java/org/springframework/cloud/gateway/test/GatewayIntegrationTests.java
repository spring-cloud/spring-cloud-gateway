package org.springframework.cloud.gateway.test;

import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.handler.GatewayPredicateHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;
import static org.springframework.web.reactive.function.client.ClientRequest.GET;
import static org.springframework.web.reactive.function.client.ClientRequest.POST;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@SuppressWarnings("unchecked")
public class GatewayIntegrationTests {

	public static final String HANDLER_MAPPER_HEADER = "X-Gateway-Handler-Mapper-Class";
	public static final String ROUTE_ID_HEADER = "X-Gateway-Route-Id";

	@LocalServerPort
	private int port;

	private WebClient webClient;

	@Before
	public void setup() {
		this.webClient = WebClient.builder(new ReactorClientHttpConnector()).build();
	}

	//TODO: remove once https://github.com/reactor/reactor-netty/issues/27 is fixed
	class Result {
		boolean passedOnce = false;
		AssertionError error = null;
	}

	@FunctionalInterface
	interface ResultVerifier {
		void verify();
	}

	private void verify(ResultVerifier verifier) {
		Result result = new Result();
		IntStream.range(0, 3).forEach( i -> {
			try {
				verifier.verify();
				result.passedOnce = true;
			} catch (AssertionError e) {
				result.error = e;
			}
		});

		if (!result.passedOnce && result.error != null) {
			throw result.error;
		}
	}

	@Test
	public void addRequestHeaderFilterWorks() {
		Mono<Map> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers")
						.header("Host", "www.addrequestheader.org")
						.build()
		).then(response -> response.body(toMono(Map.class)));

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									assertThat(response).containsKey("headers").isInstanceOf(Map.class);
									Map<String, Object> headers = (Map<String, Object>) response.get("headers");
									assertThat(headers).containsEntry("X-Request-Foo", "Bar");
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void addResponseHeaderFilterWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers")
						.header("Host", "www.addresponseheader.org")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpHeaders httpHeaders = response.headers().asHttpHeaders();
									assertThat(httpHeaders.getFirst("X-Request-Foo"))
											.isEqualTo("Bar");
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void compositeRouteWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers?foo=bar&baz")
						.header("Host", "www.foo.org")
						.header("X-Request-Id", "123")
						.cookie("chocolate", "chip")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpHeaders httpHeaders = response.headers().asHttpHeaders();
									HttpStatus statusCode = response.statusCode();
									assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
											.isEqualTo(GatewayPredicateHandlerMapping.class.getSimpleName());
									assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
											.isEqualTo("host_foo_path_headers_to_httpbin");
									assertThat(httpHeaders.getFirst("X-Response-Foo"))
											.isEqualTo("Bar");
									assertThat(statusCode).isEqualTo(HttpStatus.OK);
								})
						.expectComplete()
						.verify()
		);
	}

	@Test
	public void hostRouteWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/get")
						.header("Host", "www.example.org")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpHeaders httpHeaders = response.headers().asHttpHeaders();
									HttpStatus statusCode = response.statusCode();
									assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
											.isEqualTo(GatewayPredicateHandlerMapping.class.getSimpleName());
									assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
											.isEqualTo("host_example_to_httpbin");
									assertThat(statusCode).isEqualTo(HttpStatus.OK);
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void postWorks() {
		ClientRequest<Mono<String>> request = POST("http://localhost:" + port + "/post")
				.header("Host", "www.example.org")
				.body(Mono.just("testdata"), String.class);

		Mono<Map> result = webClient.exchange(request)
				.then(response -> response.body(toMono(Map.class)));

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(map -> assertThat(map).containsEntry("data", "testdata"))
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void removeRequestHeaderFilterWorks() {
		Mono<Map> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers")
						.header("Host", "www.removerequestheader.org")
						.header("X-Request-Foo", "Bar")
						.build()
		).then(response -> response.body(toMono(Map.class)));

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									assertThat(response).containsKey("headers").isInstanceOf(Map.class);
									Map<String, Object> headers = (Map<String, Object>) response.get("headers");
									assertThat(headers).doesNotContainKey("X-Request-Foo");
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void removeResponseHeaderFilterWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers")
						.header("Host", "www.removereresponseheader.org")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpHeaders httpHeaders = response.headers().asHttpHeaders();
									assertThat(httpHeaders).doesNotContainKey("X-Request-Foo");
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void rewritePathFilterWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/foo/get")
						.header("Host", "www.baz.org")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpStatus statusCode = response.statusCode();
									assertThat(statusCode).isEqualTo(HttpStatus.OK);
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void setPathFilterWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/foo/get")
						.header("Host", "www.setpath.org")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpStatus statusCode = response.statusCode();
									assertThat(statusCode).isEqualTo(HttpStatus.OK);
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void setResponseHeaderFilterWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers")
						.header("Host", "www.setreresponseheader.org")
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpHeaders httpHeaders = response.headers().asHttpHeaders();
									assertThat(httpHeaders).containsKey("X-Request-Foo");
									assertThat(httpHeaders.get("X-Request-Foo")).containsExactly("Bar");
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void setStatusIntWorks() {
		setStatusStringTest("www.setstatusint.org", HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void setStatusStringWorks() {
		setStatusStringTest("www.setstatusstring.org", HttpStatus.BAD_REQUEST);
	}

	private void setStatusStringTest(String host, HttpStatus status) {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/headers")
						.header("Host", host)
						.build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpStatus statusCode = response.statusCode();
									assertThat(statusCode).isEqualTo(status);
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@Test
	public void urlRouteWorks() {
		Mono<ClientResponse> result = webClient.exchange(
				GET("http://localhost:" + port + "/get").build()
		);

		verify( () ->
				StepVerifier.create(result)
						.consumeNextWith(
								response -> {
									HttpHeaders httpHeaders = response.headers().asHttpHeaders();
									HttpStatus statusCode = response.statusCode();
									assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
											.isEqualTo(GatewayPredicateHandlerMapping.class.getSimpleName());
									assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
											.isEqualTo("default_path_to_httpbin");
									assertThat(statusCode).isEqualTo(HttpStatus.OK);
								})
						.expectComplete()
						.verify(Duration.ofSeconds(3))
		);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		private static final Log log = LogFactory.getLog(TestConfig.class);

		@Bean
		@Order(500)
		public GatewayFilter modifyResponseFilter() {
			return (exchange, chain) -> {
				log.info("modifyResponseFilter start");
				String value = (String) exchange.getAttribute(GatewayFilter.GATEWAY_HANDLER_MAPPER_ATTR).orElse("N/A");
				exchange.getResponse().getHeaders().add(HANDLER_MAPPER_HEADER, value);
				Route route = (Route) exchange.getAttribute(GatewayFilter.GATEWAY_ROUTE_ATTR).orElse(null);
				if (route != null) {
					exchange.getResponse().getHeaders().add(ROUTE_ID_HEADER, route.getId());
				}
				return chain.filter(exchange);
			};
		}

		@Bean
		@Order(-1)
		public GatewayFilter postFilter() {
			return (exchange, chain) -> {
				log.info("postFilter start");
				return chain.filter(exchange).then(postFilterWork(exchange));
			};
		}

		private static Mono<Void> postFilterWork(ServerWebExchange exchange) {
			log.info("postFilterWork");
			exchange.getResponse().getHeaders().add("X-Post-Header", "AddedAfterRoute");
			return Mono.empty();
		}

	}

}
