/*
 * Copyright 2023 the original author or authors.
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

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/httpbin")
public class HttpBinCompatibleController {

	private static final Log log = LogFactory.getLog(HttpBinCompatibleController.class);

	private static final String HEADER_REQ_VARY = "X-Request-Vary";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@GetMapping("/")
	public String home() {
		return "httpbin compatible home";
	}

	@RequestMapping(path = "/headers", method = { RequestMethod.GET, RequestMethod.POST },
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> headers(HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		result.put("headers", getHeaders(request));
		return result;
	}

	/*@PatchMapping("/headers")
	public ResponseEntity<Map<String, Object>> headersPatch(ServerWebExchange exchange,
															@RequestBody Map<String, String> headersToAdd) {
		Map<String, Object> result = new HashMap<>();
		result.put("headers", getHeaders(exchange));
		ResponseEntity.BodyBuilder responseEntity = ResponseEntity.status(HttpStatus.OK);
		headersToAdd.forEach(responseEntity::header);

		return responseEntity.body(result);
	}*/

	@RequestMapping(path = "/multivalueheaders", method = { RequestMethod.GET, RequestMethod.POST },
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> multiValueHeaders(ServerWebExchange exchange) {
		Map<String, Object> result = new HashMap<>();
		result.put("headers", exchange.getRequest().getHeaders());
		return result;
	}

	/*@GetMapping(path = "/delay/{sec}/**", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Map<String, Object>> delay(ServerWebExchange exchange, @PathVariable int sec)
			throws InterruptedException {
		int delay = Math.min(sec, 10);
		return Mono.just(get(exchange)).delayElement(Duration.ofSeconds(delay));
	}*/

	@GetMapping(path = "/anything/{anything}", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> anything(HttpServletRequest request, @PathVariable(required = false) String anything) {
		return get(request);
	}

	@GetMapping(path = "/get", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> get(HttpServletRequest request) {
		if (log.isDebugEnabled()) {
			log.debug("httpbin /get");
		}
		HashMap<String, Object> result = new HashMap<>();
		Map<String, String[]> params = request.getParameterMap();
		result.put("args", params);
		result.put("headers", getHeaders(request));
		return result;
	}

	/*@PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Map<String, Object>> postFormData(@RequestBody Mono<MultiValueMap<String, Part>> parts) {
		// StringDecoder decoder = StringDecoder.allMimeTypes(true);
		return parts.flux().flatMap(map -> Flux.fromIterable(map.values())).flatMap(Flux::fromIterable)
				.filter(part -> part instanceof FilePart).reduce(new HashMap<String, Object>(), (files, part) -> {
					MediaType contentType = part.headers().getContentType();
					long contentLength = part.headers().getContentLength();
					// TODO: get part data
					files.put(part.name(), "data:" + contentType + ";base64," + contentLength);
					return files;
				}).map(files -> Collections.singletonMap("files", files));
	}*/

	/*@PostMapping(path = "/post", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Map<String, Object>> postUrlEncoded(ServerWebExchange exchange) throws IOException {
		return post(exchange, null);
	}*/

	/*@PostMapping(path = "/post", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Map<String, Object>> post(ServerWebExchange exchange, @RequestBody(required = false) String body)
			throws IOException {
		HashMap<String, Object> ret = new HashMap<>();
		ret.put("headers", getHeaders(exchange));
		ret.put("data", body);
		HashMap<String, Object> form = new HashMap<>();
		ret.put("form", form);

		return exchange.getFormData().flatMap(map -> {
			for (Map.Entry<String, List<String>> entry : map.entrySet()) {
				for (String value : entry.getValue()) {
					form.put(entry.getKey(), value);
				}
			}
			return Mono.just(ret);
		});
	}*/

	@GetMapping("/status/{status}")
	public ResponseEntity<String> status(@PathVariable int status) {
		return ResponseEntity.status(status).body("Failed with " + status);
	}

	@RequestMapping(value = "/responseheaders/{status}", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<Map<String, Object>> responseHeaders(@PathVariable int status, ServerWebExchange exchange) {
		HttpHeaders httpHeaders = exchange.getRequest().getHeaders().entrySet().stream()
				.filter(entry -> entry.getKey().startsWith("X-Test-"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(list1, list2) -> Stream.concat(list1.stream(), list2.stream()).collect(Collectors.toList()),
						HttpHeaders::new));

		return ResponseEntity.status(status).headers(httpHeaders).body(Collections.singletonMap("status", status));
	}

	@PostMapping(path = "/post/empty", produces = MediaType.APPLICATION_JSON_VALUE)
	public String emptyResponse() {
		return null;
	}

	/*@GetMapping(path = "/gzip", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Void> gzip(ServerWebExchange exchange) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("httpbin /gzip");
		}

		String jsonResponse = OBJECT_MAPPER.writeValueAsString("httpbin compatible home");
		byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

		ServerHttpResponse response = exchange.getResponse();
		response.getHeaders().add(HttpHeaders.CONTENT_ENCODING, "gzip");
		DataBufferFactory dataBufferFactory = response.bufferFactory();
		response.setStatusCode(HttpStatus.OK);

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		GZIPOutputStream is = new GZIPOutputStream(bos);
		FileCopyUtils.copy(bytes, is);

		byte[] gzippedResponse = bos.toByteArray();
		DataBuffer wrap = dataBufferFactory.wrap(gzippedResponse);
		return response.writeWith(Flux.just(wrap));
	}*/

	@GetMapping("/vary-on-header/**")
	public ResponseEntity<Map<String, Object>> varyOnAccept(HttpServletRequest request,
															@RequestHeader(name = HEADER_REQ_VARY, required = false) String headerToVary) {
		if (headerToVary == null) {
			return ResponseEntity.badRequest().body(Map.of("error", HEADER_REQ_VARY + " header is mandatory"));
		}
		else {
			var builder = ResponseEntity.ok();
			builder.varyBy(headerToVary);
			return builder.body(headers(request));
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
		// return request.headers().asHttpHeaders().toSingleValueMap();
	}

}
