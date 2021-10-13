/*
 * Copyright 2013-2020 the original author or authors.
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

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.httpclient.compression=true", "server.compression.enabled=true",
				"server.compression.min-response-size=1KB", "server.compression.mime-types=text/event-stream" })
@DirtiesContext
public class ModifyResponseBodyGatewayFilterFactorySseGzipTests extends BaseWebClientTests {

	@Test
	public void shouldModifyGzippedSseResponseBodyToSseObject() {
		Flux<ServerSentEvent<String>> result = this.webClient.get().uri("/sse")
				.header(HttpHeaders.HOST, "www.modifytoobject.org").header(HttpHeaders.ACCEPT_ENCODING, "gzip")
				.retrieve().bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				});

		StepVerifier.create(result).consumeNextWith(event -> {
			assertThat(event.data()).isEqualTo("00");
			assertThat(event.event()).isEqualTo("periodic-event");
		}).consumeNextWith(event -> {
			assertThat(event.id()).isEqualTo("1");
			assertThat(event.data()).isEqualTo("01");
			assertThat(event.event()).isEqualTo("periodic-event");
		}).expectNextCount(8).thenCancel().verify(Duration.ofSeconds(5L));

	}

	private static String extractSseEventField(String field, String event) {
		Pattern p = Pattern.compile("(?<=" + field + ":).*");
		Matcher m = p.matcher(event);

		if (m.find()) {
			return m.group(0);
		}

		return "";
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("modify_response_java_sse",
					r -> r.host("www.modifytoobject.org").filters(f -> f.modifyResponseBody(byte[].class,
							ServerSentEvent.class, (webExchange, originalResponse) -> {
								String originalEvent = new String(originalResponse);

								return Mono.just(ServerSentEvent.<String>builder()
										.event(extractSseEventField("event", originalEvent))
										.id(extractSseEventField("id", originalEvent))
										.data("0" + extractSseEventField("data", originalEvent)).build());
							})).uri(uri))
					.build();
		}

	}

}
