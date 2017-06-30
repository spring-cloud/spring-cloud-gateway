/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.sample;

import java.net.URI;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 */
@RestController
@SpringBootApplication
public class GatewaySampleApplication {

	@Value("${remote.home}")
	private URI home;

	@GetMapping(path="/test", headers="x-host=png.abc.org")
	public ResponseEntity<Object> proxy(ProxyExchange<Object> proxy) throws Exception {
		return proxy.uri(home.toString() + "/image/png")
				.get(header("X-TestHeader", "foobar"));
	}

	@GetMapping("/test2")
	public ResponseEntity<Object> proxyFoos(ProxyExchange<Object> proxy) throws Exception {
		return proxy.uri(home.toString() + "/image/webp").get(header("X-AnotherHeader", "baz"));
	}

	private Function<ResponseEntity<Object>, ResponseEntity<Object>> header(String key,
			String value) {
		return response -> ResponseEntity.status(response.getStatusCode())
				.headers(response.getHeaders()).header(key, value)
				.body(response.getBody());
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
