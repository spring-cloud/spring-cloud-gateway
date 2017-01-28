package org.springframework.cloud.gateway.test;

import java.time.Duration;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientOperations;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@SuppressWarnings("unchecked")
public class GatewayIntegrationTests {

	private static final String HANDLER_MAPPER_HEADER = "X-Gateway-Handler-Mapper-Class";
	private static final String ROUTE_ID_HEADER = "X-Gateway-Route-Id";
	private static final Duration DURATION = Duration.ofSeconds(5);

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	@LocalServerPort
	private int port;

	private WebClientOperations webClient;

	@Before
	public void setup() {
		WebClient client = WebClient.builder(new ReactorClientHttpConnector()).build();
		this.webClient = WebClientOperations.builder(client)
				.uriBuilderFactory(new DefaultUriBuilderFactory("http://localhost:" + port))
				.build();
	}

	@Test
	public void addRequestHeaderFilterWorks() {
		Mono<Map> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.addrequestheader.org")
				.exchange()
				.then(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertThat(response).containsKey("headers").isInstanceOf(Map.class);
							Map<String, Object> headers = (Map<String, Object>) response.get("headers");
							assertThat(headers).containsEntry("X-Request-Foo", "Bar");
						})
				.expectComplete()
				.verify(DURATION);
	}

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
							assertThat(response).containsKey("args").isInstanceOf(Map.class);
							Map<String, Object> args = (Map<String, Object>) response.get("args");
							assertThat(args).containsEntry("foo", "bar");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void addResponseHeaderFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.addresponseheader.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst("X-Request-Foo"))
									.isEqualTo("Bar");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void compositeRouteWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/headers?foo=bar&baz")
				.header("Host", "www.foo.org")
				.header("X-Request-Id", "123")
				.cookie("chocolate", "chip")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							HttpStatus statusCode = response.statusCode();
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(RoutePredicateHandlerMapping.class.getSimpleName());
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("host_foo_path_headers_to_httpbin");
							assertThat(httpHeaders.getFirst("X-Response-Foo"))
									.isEqualTo("Bar");
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify();
	}

	@Test
	public void hostRouteWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.header("Host", "www.example.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							HttpStatus statusCode = response.statusCode();
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(RoutePredicateHandlerMapping.class.getSimpleName());
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("host_example_to_httpbin");
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
						.verify(DURATION);
	}

	@Test
	public void hystrixFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.header("Host", "www.hystrixsuccess.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("hystrix_success_test");
							HttpStatus statusCode = response.statusCode();
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void hystrixFilterTimesout() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/delay/3")
				.header("Host", "www.hystrixfailure.org")
				.exchange();

		StepVerifier.create(result)
				.expectError() //TODO: can we get more specific as to the error?
				.verify();
	}

	@Test
	public void loadBalancerFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.header("Host", "www.loadbalancerclient.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("load_balancer_client_test");
							HttpStatus statusCode = response.statusCode();
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void postWorks() {
		Mono<Map> result = webClient.post()
				.uri("/post")
				.header("Host", "www.example.org")
				.exchange(Mono.just("testdata"), String.class)
				.then(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(map -> assertThat(map).containsEntry("data", "testdata"))
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void redirectToFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/")
				.header("Host", "www.redirectto.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpStatus statusCode = response.statusCode();
							assertThat(statusCode).isEqualTo(HttpStatus.FOUND);
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst(HttpHeaders.LOCATION))
									.isEqualTo("http://example.org");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void removeRequestHeaderFilterWorks() {
		Mono<Map> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.removerequestheader.org")
				.header("X-Request-Foo", "Bar")
				.exchange()
				.then(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertThat(response).containsKey("headers").isInstanceOf(Map.class);
							Map<String, Object> headers = (Map<String, Object>) response.get("headers");
							assertThat(headers).doesNotContainKey("X-Request-Foo");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void removeResponseHeaderFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.removereresponseheader.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders).doesNotContainKey("X-Request-Foo");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void rewritePathFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/foo/get")
				.header("Host", "www.baz.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpStatus statusCode = response.statusCode();
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void setPathFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/foo/get")
				.header("Host", "www.setpath.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpStatus statusCode = response.statusCode();
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify(DURATION);
	}

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

	@Test
	public void setStatusIntWorks() {
		setStatusStringTest("www.setstatusint.org", HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void setStatusStringWorks() {
		setStatusStringTest("www.setstatusstring.org", HttpStatus.BAD_REQUEST);
	}

	private void setStatusStringTest(String host, HttpStatus status) {
		Mono<ClientResponse> result = webClient.get()
				.uri("/headers")
				.header("Host", host)
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpStatus statusCode = response.statusCode();
							assertThat(statusCode).isEqualTo(status);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void urlRouteWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							HttpStatus statusCode = response.statusCode();
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(RoutePredicateHandlerMapping.class.getSimpleName());
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("default_path_to_httpbin");
							assertThat(statusCode).isEqualTo(HttpStatus.OK);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		private static final Log log = LogFactory.getLog(TestConfig.class);

		@Bean
		@Order(500)
		public GlobalFilter modifyResponseFilter() {
			return (exchange, chain) -> {
				log.info("modifyResponseFilter start");
				String value = (String) exchange.getAttribute(GATEWAY_HANDLER_MAPPER_ATTR).orElse("N/A");
				exchange.getResponse().getHeaders().add(HANDLER_MAPPER_HEADER, value);
				Route route = (Route) exchange.getAttribute(GATEWAY_ROUTE_ATTR).orElse(null);
				if (route != null) {
					exchange.getResponse().getHeaders().add(ROUTE_ID_HEADER, route.getId());
				}
				return chain.filter(exchange);
			};
		}

		@Bean
		@Order(-1)
		public GlobalFilter postFilter() {
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
