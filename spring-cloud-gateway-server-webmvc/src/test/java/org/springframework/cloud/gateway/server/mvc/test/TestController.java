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

package org.springframework.cloud.gateway.server.mvc.test;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/test")
public class TestController {

	private static final String HEADER_REQ_VARY = "X-Request-Vary";

	@GetMapping("/")
	public String home() {
		return "test controller home";
	}

	@RequestMapping(path = "/multivalueheaders", method = { RequestMethod.GET, RequestMethod.POST },
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> multiValueHeaders(ServerWebExchange exchange) {
		Map<String, Object> result = new HashMap<>();
		result.put("headers", exchange.getRequest().getHeaders());
		return result;
	}

	@PostMapping(path = "/localpost", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> post(HttpServletRequest request, @RequestBody(required = false) String body) {
		HashMap<String, Object> ret = new HashMap<>();
		ret.put("headers", getHeaders(request));
		ret.put("data", body);
		HashMap<String, Object> form = new HashMap<>();
		ret.put("form", form);
		return ret;
	}

	@GetMapping("/vary-on-header/**")
	public ResponseEntity<Map<String, Object>> varyOnAccept(HttpServletRequest request,
			@RequestHeader(name = HEADER_REQ_VARY, required = false) String headerToVary) {
		if (headerToVary == null) {
			return ResponseEntity.badRequest().body(Map.of("error", HEADER_REQ_VARY + " header is mandatory"));
		}
		else {
			var builder = ResponseEntity.ok();
			builder.varyBy(headerToVary);
			Map<String, Object> result = new HashMap<>();
			result.put("headers", getHeaders(request));
			return builder.body(result);
		}
	}

	public Map<String, String> getHeaders(HttpServletRequest req) {
		HashMap<String, String> headers = new HashMap<>();
		Enumeration<String> headerNames = req.getHeaderNames();

		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			String value = null;

			Enumeration<String> values = req.getHeaders(name);
			if (values.hasMoreElements()) {
				value = values.nextElement();
			}
			headers.put(name, value);
		}
		return headers;
	}

}
