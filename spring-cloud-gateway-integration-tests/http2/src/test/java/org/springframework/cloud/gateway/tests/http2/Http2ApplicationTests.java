/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.gateway.tests.http2;

import java.time.Duration;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = Http2Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class Http2ApplicationTests {

	@LocalServerPort
	int port;

	@Test
	public void http2Works(CapturedOutput output) {
		Hooks.onOperatorDebug();
		String uri = "https://localhost:" + port + "/myprefix/hello";
		String expected = "Hello";
		assertResponse(uri, expected);
		Assertions.assertThat(output).contains("Negotiated application-level protocol [h2]", "PRI * HTTP/2.0");
	}

	public static void assertResponse(String uri, String expected) {
		WebClient client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(getHttpClient())).build();
		Mono<ResponseEntity<String>> responseEntityMono = client.get().uri(uri).retrieve().toEntity(String.class);
		StepVerifier.create(responseEntityMono).assertNext(entity -> {
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(entity.getBody()).isEqualTo(expected);
		}).expectComplete().verify();
	}

	static HttpClient getHttpClient() {
		return HttpClient
				.create(ConnectionProvider.builder("test").maxConnections(100)
						.pendingAcquireTimeout(Duration.ofMillis(0)).pendingAcquireMaxCount(-1).build())
				.protocol(HttpProtocol.HTTP11, HttpProtocol.H2).secure(sslContextSpec -> {
					Http2SslContextSpec clientSslCtxt = Http2SslContextSpec.forClient()
							.configure(builder -> builder.trustManager(InsecureTrustManagerFactory.INSTANCE));
					sslContextSpec.sslContext(clientSslCtxt);
				});
	}

}
