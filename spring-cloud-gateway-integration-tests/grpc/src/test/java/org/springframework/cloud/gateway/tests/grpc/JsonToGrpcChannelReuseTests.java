/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.gateway.tests.grpc;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.config.GrpcSslConfigurer;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.factory.JsonToGrpcGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestTemplate;

/**
 * Verifies that {@link JsonToGrpcGatewayFilterFactory} reuses a single
 * {@link ManagedChannel} per route. Uses its own application context so that routes
 * registered by other tests do not interfere.
 */
@SpringBootTest(properties = JsonToGrpcGatewayFilterFactory.VALID_PROTO_DESCRIPTOR_PREFIXES_KEY
		+ "=classpath:,classpath*:,file:src/main/proto", webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class JsonToGrpcChannelReuseTests {

	@LocalServerPort
	private int gatewayPort;

	@Autowired
	private CountingGrpcSslConfigurer grpcSslConfigurer;

	@Test
	public void shouldReuseManagedChannelAcrossRequests() {
		new RouteConfigurer(gatewayPort).addRoute(gatewayPort + 1, "/json/hello",
				"JsonToGrpc=HelloService,hello,file:src/main/proto/hello.pb");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>("{\"firstName\":\"Duff\", \"lastName\":\"McKagan\"}", headers);
		RestTemplate restTemplate = createUnsecureClient();

		for (int i = 0; i < 3; i++) {
			String response = restTemplate
				.postForEntity("https://localhost:" + this.gatewayPort + "/json/hello", request, String.class)
				.getBody();
			Assertions.assertThat(response).isNotNull().contains("{\"greeting\":\"Hello, Duff McKagan\"}");
		}

		// a single channel is created for the route and reused by every request
		Assertions.assertThat(grpcSslConfigurer.channelsCreated.get()).isEqualTo(1);
	}

	@SuppressWarnings("deprecation")
	private RestTemplate createUnsecureClient() {
		TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
		SSLContext sslContext;
		try {
			sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
		}
		catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
			throw new RuntimeException(e);
		}
		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
				NoopHostnameVerifier.INSTANCE);

		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
			.register("https", sslSocketFactory)
			.register("http", new PlainConnectionSocketFactory())
			.build();

		HttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);
		CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();

		return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		CountingGrpcSslConfigurer grpcSslConfigurer(HttpClientProperties properties, SslBundles bundles) {
			return new CountingGrpcSslConfigurer(properties, bundles);
		}

	}

	static class CountingGrpcSslConfigurer extends GrpcSslConfigurer {

		final AtomicInteger channelsCreated = new AtomicInteger();

		CountingGrpcSslConfigurer(HttpClientProperties properties, SslBundles bundles) {
			super(properties.getSsl(), bundles);
		}

		@Override
		public ManagedChannel configureSsl(NettyChannelBuilder nettyChannelBuilder) throws SSLException {
			channelsCreated.incrementAndGet();
			return super.configureSsl(nettyChannelBuilder);
		}

	}

}
