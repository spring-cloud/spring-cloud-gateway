/*
 * Copyright 2013-2023 the original author or authors.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class RouteConfigurer {

	private final int actuatorPort;

	private final RestTemplate restTemplate;

	RouteConfigurer(int actuatorPort) {
		this.actuatorPort = actuatorPort;
		this.restTemplate = createUnsecureClient();
	}

	public void addRoute(int grpcServerPort, String path, String filter) {
		final String routeId = "test-route-" + UUID.randomUUID();

		Map<String, Object> route = new HashMap<>();
		route.put("id", routeId);
		route.put("uri", "https://localhost:" + grpcServerPort);
		route.put("predicates", Collections.singletonList("Path=" + path));
		if (filter != null) {
			route.put("filters", Arrays.asList(filter));
		}

		ResponseEntity<String> exchange = restTemplate.exchange(url("/actuator/gateway/routes/" + routeId),
				HttpMethod.POST, new HttpEntity<>(route), String.class);

		assert exchange.getStatusCode() == HttpStatus.CREATED;

		refreshRoutes();
	}

	private void refreshRoutes() {
		ResponseEntity<String> exchange = restTemplate.exchange(url("/actuator/gateway/refresh"), HttpMethod.POST,
				new HttpEntity<>(""), String.class);

		assert exchange.getStatusCode() == HttpStatus.OK;
	}

	private String url(String context) {
		return String.format("https://localhost:%s%s", this.actuatorPort, context);
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
