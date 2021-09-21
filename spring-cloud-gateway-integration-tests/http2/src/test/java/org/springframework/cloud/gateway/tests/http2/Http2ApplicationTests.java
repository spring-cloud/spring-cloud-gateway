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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathException;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathFailureAnalyzer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.*;

/**
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class Http2ApplicationTests {

	@Autowired
	private ServerProperties serverProperties;

	@LocalServerPort
	int port;

	@Test
	public void http2Works(CapturedOutput output) {
		HttpClient httpClient = HttpClient.create(ConnectionProvider.builder("test").maxConnections(100)
						.pendingAcquireTimeout(Duration.ofMillis(0))
						.pendingAcquireMaxCount(-1).build())
				.protocol(HttpProtocol.HTTP11, HttpProtocol.H2)
				.secure(sslContextSpec -> {
					Http2SslContextSpec clientSslCtxt =
							Http2SslContextSpec.forClient()
									.configure(builder -> builder.trustManager(InsecureTrustManagerFactory.INSTANCE));
					sslContextSpec.sslContext(clientSslCtxt);
				});
		Flux<HttpClientResponse> responseFlux = httpClient.request(HttpMethod.GET)
				.uri("https://localhost:" + port + "/myprefix/hello")
				.send(Mono.empty())
				.response((res, byteBufFlux) -> {
					assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
					return Mono.just(res);
				});


		StepVerifier.create(responseFlux).expectNextCount(1).expectComplete().verify();
		Assertions.assertThat(output).contains("Negotiated application-level protocol [h2]", "PRI * HTTP/2.0");
	}


}
