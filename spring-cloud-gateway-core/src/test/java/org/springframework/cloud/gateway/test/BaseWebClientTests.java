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

package org.springframework.cloud.gateway.test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class BaseWebClientTests {

	protected static final String HANDLER_MAPPER_HEADER = "X-Gateway-Handler-Mapper-Class";
	protected static final String ROUTE_ID_HEADER = "X-Gateway-RouteDefinition-Id";
	protected static final Duration DURATION = Duration.ofSeconds(5);

	@LocalServerPort
	protected int port = 0;

	protected WebTestClient testClient;
	protected WebClient webClient;
	protected String baseUri;

	@Before
	public void setup() {
		//TODO: how to set new ReactorClientHttpConnector()
		baseUri = "http://localhost:" + port;
		this.webClient = WebClient.create(baseUri);
		this.testClient = WebTestClient.bindToServer().baseUrl(baseUri).build();
	}

	@RestController
	@RequestMapping("/httpbin")
	@Configuration
	@RibbonClients({
		@RibbonClient(name = "testservice", configuration = TestRibbonConfig.class),
		@RibbonClient(name = "myservice", configuration = TestRibbonConfig.class)
	})
	@Import(PermitAllSecurityConfiguration.class)
	protected static class DefaultTestConfig {

		private static final Log log = LogFactory.getLog(DefaultTestConfig.class);

		@RequestMapping("/")
		public String home(ServerWebExchange exchange) {
			return "httpbin compatible home";
		}

		@RequestMapping(path = "/headers", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
		public Map<String, Object> headers(ServerWebExchange exchange) {
			HashMap<String, Object> map = new HashMap<>();
			addHeaders(exchange, map);
			return map;
		}

		private void addHeaders(ServerWebExchange exchange, HashMap<String, Object> map) {
			HashMap<String, String> headers = new HashMap<>();
			exchange.getRequest().getHeaders().forEach((name, values) -> {
				if (log.isDebugEnabled()) {
					log.debug("Header, name: "+name+", "+values);
				}
				headers.put(name, values.get(0));
			});

			map.put("headers", headers);
		}

		@RequestMapping(path = "/delay/{sec}", produces = MediaType.APPLICATION_JSON_VALUE)
		public Map<String, Object> get(ServerWebExchange exchange, @PathVariable int sec) throws InterruptedException {
			int delay = Math.min(sec, 10);
			Thread.sleep(delay * 1000);
			return get(exchange);
		}

		@RequestMapping(path = "/get", produces = MediaType.APPLICATION_JSON_VALUE)
		public Map<String, Object> get(ServerWebExchange exchange) {
			HashMap<String, Object> map = new HashMap<>();
			addHeaders(exchange, map);
			HashMap<String, String> params = new HashMap<>();
			exchange.getRequest().getQueryParams().forEach((name, values) -> {
				params.put(name, values.get(0));

			});
			map.put("args", params);
			return map;
		}

		@RequestMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
		public Mono<Map<String, Object>> postFormData(@RequestBody Mono<MultiValueMap<String, Part>> parts) {
			// StringDecoder decoder = StringDecoder.allMimeTypes(true);
			return parts.flux().flatMap(map -> Flux.fromIterable(map.values()))
					.flatMap(map -> Flux.fromIterable(map))
					.filter(part -> part instanceof FilePart)
					.reduce(new HashMap<String, Object>(), (files, part) -> {
						MediaType contentType = part.headers().getContentType();
						long contentLength = part.headers().getContentLength();
						files.put(part.name(), "data:"+contentType+";base64,"+contentLength); //TODO: get part data
						return files;
					}).map(files -> Collections.singletonMap("files", files));
		}

		@RequestMapping(path = "/post", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
		public Mono<Map<String, Object>> postUrlEncoded(ServerWebExchange exchange) throws IOException {
			return post(exchange, null);
		}

		@RequestMapping(path = "/post", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
		public Mono<Map<String, Object>> post(ServerWebExchange exchange,
											  @RequestBody(required = false) String body) throws IOException {
			HashMap<String, Object> ret = new HashMap<>();
			ret.put("data", body);
			HashMap<String, Object> form = new HashMap<>();
			ret.put("form", form);

			return exchange.getFormData().flatMap(map -> {
				for (Map.Entry<String, List<String>> entry: map.entrySet()) {
					for (String value : entry.getValue()) {
						form.put(entry.getKey(), value);
					}
				}
				return Mono.just(ret);
			});
		}

		@RequestMapping("/status/{status}")
		public ResponseEntity<String> status(@PathVariable int status) {
			return ResponseEntity.status(status).body("Failed with "+status);
		}

		@Bean
		@Order(500)
		public GlobalFilter modifyResponseFilter() {
			return (exchange, chain) -> {
				log.info("modifyResponseFilter start");
				String value = exchange.getAttributeOrDefault(GATEWAY_HANDLER_MAPPER_ATTR, "N/A");
				exchange.getResponse().getHeaders().add(HANDLER_MAPPER_HEADER, value);
				Route route = exchange.getAttributeOrDefault(GATEWAY_ROUTE_ATTR,null);
				if (route != null) {
					exchange.getResponse().getHeaders().add(ROUTE_ID_HEADER, route.getId());
				}
				return chain.filter(exchange);
			};
		}
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class MainConfig { }

	public static void main(String[] args) {
		new SpringApplication(MainConfig.class).run(args);
	}

	protected static class TestRibbonConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("localhost", this.port));
		}
	}

}
