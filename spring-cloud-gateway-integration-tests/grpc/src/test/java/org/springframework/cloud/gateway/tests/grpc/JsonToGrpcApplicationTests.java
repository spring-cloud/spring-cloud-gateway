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

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * @author Alberto C. Ríos
 * @author Abel Salgado Romero
 */
@Disabled
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class JsonToGrpcApplicationTests {

	@LocalServerPort
	private int gatewayPort;

	private RestTemplate restTemplate;

	@BeforeEach
	void setUp() {
		restTemplate = createUnsecureClient();
	}

	@Test
	public void shouldConvertFromJSONToGRPC() {
		// Since GRPC server and GW run in same instance and don't know server port until
		// test starts,
		// we need to configure route dynamically using the actuator endpoint.
		final RouteConfigurer configurer = new RouteConfigurer(gatewayPort);
		int grpcServerPort = gatewayPort + 1;
		configurer.addRoute(grpcServerPort, "/json/hello",
				"JsonToGrpc=file:src/main/proto/hello.pb,file:src/main/proto/hello.proto,HelloService,hello");

		String response = restTemplate
			.postForEntity("https://localhost:" + this.gatewayPort + "/json/hello",
					"{\"firstName\":\"Duff\", \"lastName\":\"McKagan\"}", String.class)
			.getBody();

		Assertions.assertThat(response).isNotNull();
		Assertions.assertThat(response).contains("{\"greeting\":\"Hello, Duff McKagan\"}");
	}

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

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		return new RestTemplate(requestFactory);
	}

}
