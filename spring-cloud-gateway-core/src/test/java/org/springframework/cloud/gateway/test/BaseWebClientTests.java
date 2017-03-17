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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.EnableGateway;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.model.Route;
// import org.springframework.cloud.netflix.ribbon.RibbonClient;
// import org.springframework.cloud.netflix.ribbon.RibbonClients;
// import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

// import com.netflix.loadbalancer.Server;
// import com.netflix.loadbalancer.ServerList;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.test.TestUtils.parseMultipart;

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

	protected WebClient webClient;
	protected String baseUri;

	@Before
	public void setup() {
		//TODO: how to set new ReactorClientHttpConnector()
		baseUri = "http://localhost:" + port;
		this.webClient = WebClient.create(baseUri);
	}

	@RestController
	@RequestMapping("/httpbin")
	@Configuration
	/*@RibbonClients({
		@RibbonClient(name = "testservice", configuration = TestRibbonConfig.class),
		@RibbonClient(name = "myservice", configuration = TestRibbonConfig.class)
	})*/
	@EnableGateway
	protected static class DefaultTestConfig {

		private static final Log log = LogFactory.getLog(DefaultTestConfig.class);

		@RequestMapping("/")
		public String home(ServerWebExchange exchange) {
			return "httpbin compatible home";
		}

		@RequestMapping("/headers")
		public Map<String, Object> headers(ServerWebExchange exchange) {
			HashMap<String, Object> map = new HashMap<>();
			addHeaders(exchange, map);
			return map;
		}

		private void addHeaders(ServerWebExchange exchange, HashMap<String, Object> map) {
			HashMap<String, String> headers = new HashMap<>();
			exchange.getRequest().getHeaders().forEach((name, values) -> {
				headers.put(name, values.get(0));
			});

			map.put("headers", headers);
		}

		@RequestMapping("/delay/{sec}")
		public Map<String, Object> get(ServerWebExchange exchange, @PathVariable int sec) throws InterruptedException {
			int delay = Math.min(sec, 10);
			Thread.sleep(delay * 1000);
			return get(exchange);
		}

		@RequestMapping("/get")
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

		@RequestMapping(value = "/post", consumes = "multipart/form-data")
		public Mono<Map<String, Object>> postFormData(ServerWebExchange exchange,
											  @RequestBody(required = false) String body) {
			HashMap<String, Object> ret = new HashMap<>();
			HashMap<String, Object> files = parseMultipart(exchange, body);


			ret.put("files", files);
			return Mono.just(ret);
		}

		@RequestMapping("/post")
		public Mono<Map<String, Object>> post(ServerWebExchange exchange,
											  @RequestBody(required = false) String body) throws IOException {
			HashMap<String, Object> ret = new HashMap<>();
			ret.put("data", body);
			HashMap<String, Object> form = new HashMap<>();
			ret.put("form", form);

			return exchange.getFormData().then(map -> {
				for (Map.Entry<String, List<String>> entry: map.entrySet()) {
					for (String value : entry.getValue()) {
						form.put(entry.getKey(), value);
					}
				}
				return Mono.just(ret);
			});
		}

		@Bean
		@Order(500)
		public GlobalFilter modifyResponseFilter() {
			return (exchange, chain) -> {
				log.info("modifyResponseFilter start");
				String value = (String) exchange.getAttribute(GATEWAY_HANDLER_MAPPER_ATTR).orElse("N/A");
				exchange.getResponse().getHeaders().add(HANDLER_MAPPER_HEADER, value);
				Route route = (Route) exchange.getAttribute(GATEWAY_ROUTE_ATTR).orElse(null);
				if (route != null) {
					exchange.getResponse().getHeaders().add(ROUTE_ID_HEADER, route.getId());
				}
				return chain.filter(exchange);
			};
		}
	}

	protected static class TestRibbonConfig {

		@LocalServerPort
		protected int port = 0;

		/*@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("localhost", this.port));
		}*/
	}

}
