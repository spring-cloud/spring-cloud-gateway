/*
 * Copyright 2013-2020 the original author or authors.
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
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(properties = {}, webEnvironment = RANDOM_PORT)
@ActiveProfiles("transferencoding")
public class TransferEncodingNormalizationHeadersFilterIntegrationTests {

	private static final Log log = LogFactory.getLog(TransferEncodingNormalizationHeadersFilterIntegrationTests.class);

	@LocalServerPort
	private int port;

	@Test
	void legitRequestShouldNotFail() throws Exception {
		final ClassLoader classLoader = this.getClass().getClassLoader();

		// Issue a crafted request with smuggling attempt
		assert200With("Should Fail",
				StreamUtils.copyToByteArray(classLoader.getResourceAsStream("transfer-encoding/invalid-request.bin")));

		// Issue a legit request, which should not fail
		assert200With("Should Not Fail",
				StreamUtils.copyToByteArray(classLoader.getResourceAsStream("transfer-encoding/valid-request.bin")));
	}

	private void assert200With(String name, byte[] payload) throws Exception {
		final String response = execute("localhost", port, payload);
		log.info(LogMessage.format("Request to localhost:%d %s\n%s", port, name, new String(payload)));
		assertThat(response).isNotNull();
		log.info(LogMessage.format("Response %s\n%s", name, response));
		assertThat(response).matches("HTTP/1.\\d 200 OK");
	}

	private String execute(String target, int port, byte[] payload) throws IOException {
		final Socket socket = new Socket(target, port);

		final OutputStream out = socket.getOutputStream();
		final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		out.write(payload);

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
