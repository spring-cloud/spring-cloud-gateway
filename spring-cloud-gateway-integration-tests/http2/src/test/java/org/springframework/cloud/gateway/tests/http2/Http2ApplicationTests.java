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

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class Http2ApplicationTests {

	private static Log log = LogFactory.getLog(Http2ApplicationTests.class);

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
		Flux<HttpClientResponse> responseFlux = getHttpClient().request(HttpMethod.GET).uri(uri).send(Mono.empty())
				.response((res, byteBufFlux) -> {
					assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
					NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
					return DataBufferUtils.join(byteBufFlux.map(bufferFactory::wrap))
							.map(dataBuffer -> dataBuffer.toString(StandardCharsets.UTF_8)).map(s -> {
								assertThat(s).isEqualTo(expected);
								return res;
							});
				}).onErrorContinue((throwable, o) -> log.error("Error connecting to uri " + uri, throwable));

		StepVerifier.create(responseFlux).expectNextCount(1).expectComplete().verify();
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
