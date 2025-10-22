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

package org.springframework.cloud.gateway.filter.headers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.log.LogMessage;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = TransferEncodingNormalizationHeadersFilterIntegrationTests.TestConfig.class,
		webEnvironment = RANDOM_PORT)
@ActiveProfiles("transferencoding")
public class TransferEncodingNormalizationHeadersFilterIntegrationTests {

	private static final Log log = LogFactory.getLog(TransferEncodingNormalizationHeadersFilterIntegrationTests.class);

	private static final String validRequest = "POST /route/echo HTTP/1.1\r\n" + "Host: localhost:8080\r\n"
			+ "Content-Type: application/json\r\n" + "Content-Length: 15\r\n" + "Connection: close\r\n" + "\r\n"
			+ "{\"message\":\"3\"}";

	private static final String invalidRequest = "POST /route/echo HTTP/1.0\r\n" + "Host: localhost:8080\r\n"
			+ "Content-Length: 19\r\n" + "Transfer-encoding: Chunked\r\n" + "Content-Type: application/json\r\n"
			+ "Connection: close\r\n" + "\r\n" + "22\r\n" + "{\"message\":\"3\"}\r\n" + "\r\n"
			+ "GET /nonexistantpath123 HTTP/1.0\r\n" + "0\r\n" + "\r\n";

	@LocalServerPort
	private int port;

	@Test
	void invalidRequestShouldFail() throws Exception {
		// Issue a crafted request with smuggling attempt
		assertStatus("Should Fail", invalidRequest, "400 Bad Request");
	}

	@Test
	void legitRequestShouldNotFail() throws Exception {
		// Issue a legit request, which should not fail
		assertStatus("Should Not Fail", validRequest, "200 OK");
	}

	private void assertStatus(String name, String payloadString, String status) throws Exception {
		// String payloadString = new String(payload);
		payloadString = payloadString.replace("8080", "" + port);

		log.info(LogMessage.format("Request to localhost:%d %s\n%s", port, name, payloadString));
		final String response = execute("localhost", port, payloadString);
		assertThat(response).isNotNull();
		log.info(LogMessage.format("Response %s\n%s", name, response));
		assertThat(response).matches("HTTP/1.\\d " + status);
	}

	private String execute(String target, int port, String payload) throws IOException {
		final Socket socket = new Socket(target, port);

		final OutputStream out = socket.getOutputStream();
		final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		out.write(payload.getBytes(StandardCharsets.UTF_8));

		final String headResponse = in.readLine();

		out.close();
		in.close();

		return headResponse;
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	@LoadBalancerClient(name = "xferenc", configuration = TestLoadBalancerConfig.class)
	@RestController
	public static class TestConfig {

		@PostMapping(value = "/echo", produces = { MediaType.APPLICATION_JSON_VALUE })
		public Message message(@RequestBody Message message) throws IOException {
			return message;
		}

		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("echo", r -> r.path("/route/echo").filters(f -> f.stripPrefix(1)).uri("lb://xferenc"))
				.build();
		}

	}

	public static class Message {

		private String message;

		public Message(@JsonProperty("message") String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

	public static class TestLoadBalancerConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("xferenc",
					new DefaultServiceInstance("xferenc" + "-1", "xferenc", "localhost", port, false));
		}

	}

}
