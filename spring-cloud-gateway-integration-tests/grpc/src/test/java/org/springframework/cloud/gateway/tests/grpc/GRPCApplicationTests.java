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

package org.springframework.cloud.gateway.tests.grpc;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.grpc.netty.NegotiationType.TLS;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * @author Alberto C. Ríos
 */
@SpringBootTest(classes = org.springframework.cloud.gateway.tests.grpc.GRPCApplication.class,
		webEnvironment = WebEnvironment.RANDOM_PORT)
public class GRPCApplicationTests {

	@LocalServerPort
	private int port;

	@Test
	public void gRPCUnaryCalShouldReturnResponse() throws SSLException {
		ManagedChannel channel = createSecuredChannel(port + 1);

		final HelloResponse response = HelloServiceGrpc.newBlockingStub(channel)
				.hello(HelloRequest.newBuilder().setFirstName("Sir").setLastName("FromClient").build());

		Assertions.assertThat(response.getGreeting()).isEqualTo("Hello, Sir FromClient");
	}

	private ManagedChannel createSecuredChannel(int port) throws SSLException {
		TrustManager[] trustAllCerts = createTrustAllTrustManager();

		return NettyChannelBuilder.forAddress("localhost", port).useTransportSecurity()
				.sslContext(GrpcSslContexts.forClient().trustManager(trustAllCerts[0]).build()).negotiationType(TLS)
				.build();
	}

	private TrustManager[] createTrustAllTrustManager() {
		return new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
	}

}
