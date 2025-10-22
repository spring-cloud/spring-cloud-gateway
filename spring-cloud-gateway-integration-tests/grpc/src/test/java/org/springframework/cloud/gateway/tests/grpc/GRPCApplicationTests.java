/*
 * Copyright 2013-present the original author or authors.
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
import java.util.Iterator;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import static io.grpc.Status.FAILED_PRECONDITION;
import static io.grpc.Status.RESOURCE_EXHAUSTED;
import static io.grpc.netty.NegotiationType.TLS;

/**
 * @author Alberto C. Ríos
 */
@SpringBootTest(classes = org.springframework.cloud.gateway.tests.grpc.GRPCApplication.class,
		webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class GRPCApplicationTests {

	@LocalServerPort
	private int gatewayPort;

	public static void main(String[] args) {
		SpringApplication.run(GRPCApplication.class, args);
	}

	@BeforeEach
	void setUp() {
		int grpcServerPort = gatewayPort + 1;
		final RouteConfigurer configurer = new RouteConfigurer(gatewayPort);
		configurer.addRoute(grpcServerPort, "/**", null);
	}

	@Test
	public void gRPCUnaryCallShouldReturnResponse() throws SSLException {
		ManagedChannel channel = createSecuredChannel(gatewayPort);

		final HelloResponse response = HelloServiceGrpc.newBlockingStub(channel)
			.hello(HelloRequest.newBuilder().setFirstName("Sir").setLastName("FromClient").build());

		Assertions.assertThat(response.getGreeting()).isEqualTo("Hello, Sir FromClient");
	}

	@Test
	public void gRPCStreamingCallShouldReturnResponse() throws SSLException {
		ManagedChannel channel = createSecuredChannel(gatewayPort);

		final Iterator<HelloResponse> response = StreamServiceGrpc.newBlockingStub(channel)
			.more(HelloRequest.newBuilder().setFirstName("Sir").setLastName("FromClient").build());

		Assertions.assertThat(response.next().getGreeting()).isEqualTo("Hello(0) ==> Sir");
		Assertions.assertThat(response.next().getGreeting()).isEqualTo("Hello(1) ==> Sir");
		Assertions.assertThat(response.next().getGreeting()).isEqualTo("Hello(2) ==> Sir");
	}

	private ManagedChannel createSecuredChannel(int port) throws SSLException {
		TrustManager[] trustAllCerts = createTrustAllTrustManager();

		return NettyChannelBuilder.forAddress("localhost", port)
			.useTransportSecurity()
			.sslContext(GrpcSslContexts.forClient().trustManager(trustAllCerts[0]).build())
			.negotiationType(TLS)
			.build();
	}

	@Test
	public void gRPCUnaryCallShouldHandleRuntimeException() throws SSLException {
		ManagedChannel channel = createSecuredChannel(gatewayPort);

		try {
			HelloServiceGrpc.newBlockingStub(channel)
				.hello(HelloRequest.newBuilder().setFirstName("failWithRuntimeException!").build());
		}
		catch (StatusRuntimeException e) {
			Assertions.assertThat(FAILED_PRECONDITION.getCode()).isEqualTo(e.getStatus().getCode());
			Assertions.assertThat("Invalid firstName").isEqualTo(e.getStatus().getDescription());
		}
	}

	@Test
	public void gRPCUnaryCallShouldHandleRuntimeExceptionAfterData() throws SSLException {
		ManagedChannel channel = createSecuredChannel(gatewayPort);
		boolean thrown = false;
		try {
			HelloServiceGrpc.newBlockingStub(channel)
				.hello(HelloRequest.newBuilder().setFirstName("failWithRuntimeExceptionAfterData!").build())
				.getGreeting();
		}
		catch (StatusRuntimeException e) {
			thrown = true;
			Assertions.assertThat(e.getStatus().getCode()).isEqualTo(RESOURCE_EXHAUSTED.getCode());
			Assertions.assertThat(e.getStatus().getDescription()).isEqualTo("Too long firstNames?");
		}
		Assertions.assertThat(thrown).withFailMessage("Expected exception not thrown!").isTrue();
	}

	@Test
	public void gRPCStreamingCallShouldHandleRuntimeExceptionAfterData() throws SSLException {
		ManagedChannel channel = createSecuredChannel(gatewayPort);
		boolean thrown = false;
		final Iterator<HelloResponse> response = StreamServiceGrpc.newBlockingStub(channel)
			.more(HelloRequest.newBuilder()
				.setFirstName("failWithRuntimeExceptionAfterData!")
				.setLastName("FromClient")
				.build());
		Assertions.assertThat(response.next().getGreeting())
			.isEqualTo("Hello(0) ==> failWithRuntimeExceptionAfterData!");
		Assertions.assertThat(response.next().getGreeting())
			.isEqualTo("Hello(1) ==> failWithRuntimeExceptionAfterData!");
		try {
			Assertions.assertThat(response.next().getGreeting())
				.isEqualTo("Hello(2) ==> failWithRuntimeExceptionAfterData!");
		}
		catch (StatusRuntimeException e) {
			thrown = true;
			Assertions.assertThat(e.getStatus().getCode()).isEqualTo(RESOURCE_EXHAUSTED.getCode());
			Assertions.assertThat(e.getStatus().getDescription()).isEqualTo("Too long firstNames?");
		}
		Assertions.assertThat(thrown).withFailMessage("Expected exception not thrown!").isTrue();
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
