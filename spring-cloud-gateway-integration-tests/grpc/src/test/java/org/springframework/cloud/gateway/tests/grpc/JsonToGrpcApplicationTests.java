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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * @author Alberto C. Ríos
 * @author Abel Salgado Romero
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class JsonToGrpcApplicationTests {

	@LocalServerPort
	private int gatewayPort;

	private RestTemplate restTemplate;

	@BeforeEach
	void setUp() {
		restTemplate = RouteConfigurer.createUnsecureClient();
	}

	@Test
	public void shouldConvertFromJSONToGRPC() {
		// Since GRPC server and GW run in same instance and don't know server port until
		// test starts,
		// we need to configure route dynamically using the actuator endpoint.
		final RouteConfigurer configurer = new RouteConfigurer(gatewayPort);
		int grpcServerPort = gatewayPort + 1;
		configurer.addRoute(grpcServerPort, "/json/hello",
				"JsonToGrpc=file:src/main/proto/hello.pb,file:src/main/proto/hello.proto,HelloService,hello",
				"SetResponseHeader=Content-Type,application/json");

		ResponseEntity<String> responseEntity = restTemplate.postForEntity(
				"https://localhost:" + this.gatewayPort + "/json/hello",
				"{\"firstName\":\"Duff\", \"lastName\":\"McKagan\"}", String.class);
		Assertions.assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		String response = responseEntity.getBody();

		Assertions.assertThat(response).isNotNull();
		Assertions.assertThat(response).contains("{\"greeting\":\"Hello, Duff McKagan\"}");
	}

}
