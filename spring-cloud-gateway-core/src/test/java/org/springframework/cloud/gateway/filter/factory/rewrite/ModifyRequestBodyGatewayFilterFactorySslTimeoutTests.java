/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RepeatFailedTest;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author fangfeikun
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.httpclient.ssl.handshake-timeout=1ms",
				"spring.main.allow-bean-definition-overriding=true" })
@DirtiesContext
@ActiveProfiles("single-cert-ssl")
public class ModifyRequestBodyGatewayFilterFactorySslTimeoutTests
		extends BaseWebClientTests {

	@Autowired
	AtomicInteger releaseCount;

	@BeforeEach
	public void setup() {
		try {
			SslContext sslContext = SslContextBuilder.forClient()
					.trustManager(InsecureTrustManagerFactory.INSTANCE).build();
			HttpClient httpClient = HttpClient.create()
					.secure(ssl -> ssl.sslContext(sslContext));
			setup(new ReactorClientHttpConnector(httpClient),
					"https://localhost:" + port);
		}
		catch (SSLException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void modifyRequestBodySSLTimeout() {
		testClient.post().uri("/post")
				.header("Host", "www.modifyrequestbodyssltimeout.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
				.body(BodyInserters.fromValue("request")).exchange().expectStatus()
				.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR).expectBody()
				.jsonPath("message").isEqualTo("handshake timed out after 1ms");
	}

	@RepeatFailedTest(3)
	public void modifyRequestBodyRelease() {
		releaseCount.set(0);
		// long initialUsedDirectMemory = PlatformDependent.usedDirectMemory();
		for (int i = 0; i < 10; i++) {
			testClient.post().uri("/post")
					.header("Host", "www.modifyrequestbodyssltimeout.org")
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
					.body(BodyInserters.fromValue("request")).exchange().expectStatus()
					.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			long usedDirectMemory = PlatformDependent.usedDirectMemory();
			// Assert.assertTrue(usedDirectMemory - initialUsedDirectMemory < 2 * 10 * 10
			// * 1024 * 1024);
		}
		assertThat(releaseCount).hasValue(10);
	}

	@Test
	public void modifyRequestBodyHappenedError() {
		testClient.post().uri("/post")
				.header("Host", "www.modifyrequestbodyexception.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
				.body(BodyInserters.fromValue("request")).exchange().expectStatus()
				.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR).expectBody()
				.jsonPath("message").isEqualTo("modify body exception");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration(proxyBeanMethods = false)
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		@DependsOn("testModifyRequestBodyGatewayFilterFactory")
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("test_modify_request_body_ssl_timeout",
					r -> r.order(-1).host("**.modifyrequestbodyssltimeout.org")
							.filters(f -> f.modifyRequestBody(String.class, String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(serverWebExchange, aVoid) -> {
										byte[] largeBody = new byte[10 * 1024 * 1024];
										return Mono.just(new String(largeBody));
									}))
							.uri(uri))
					.route("test_modify_request_body_exception", r -> r.order(-1)
							.host("**.modifyrequestbodyexception.org")
							.filters(f -> f.modifyRequestBody(String.class, String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(serverWebExchange, body) -> {
										return Mono.error(
												new Exception("modify body exception"));
									}))
							.uri(uri))
					.build();
		}

		@Bean
		public AtomicInteger count() {
			return new AtomicInteger();
		}

		@Bean
		@Primary
		public ModifyRequestBodyGatewayFilterFactory testModifyRequestBodyGatewayFilterFactory(
				ServerCodecConfigurer codecConfigurer, AtomicInteger count) {
			return new ModifyRequestBodyGatewayFilterFactory(
					codecConfigurer.getReaders()) {
				@Override
				protected Mono<Void> release(ServerWebExchange exchange,
						CachedBodyOutputMessage outputMessage, Throwable throwable) {
					if (outputMessage.isCached()) {
						count.incrementAndGet();
					}
					return super.release(exchange, outputMessage, throwable);
				}
			};
		}

	}

}
