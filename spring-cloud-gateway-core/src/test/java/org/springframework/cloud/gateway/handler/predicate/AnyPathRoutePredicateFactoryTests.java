/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.junit.Test;
import org.springframework.cloud.gateway.handler.predicate.AnyPathRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class AnyPathRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void noPathMatch() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com")
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		Predicate<ServerWebExchange> predicate = new AnyPathRoutePredicateFactory()
                		.apply(new Config().setPatterns("/path", "/path/extra/**"));

		assertThat(predicate.test(exchange)).isFalse();
	}

	@Test
	public void plainPathMatch() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com/path")
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		Predicate<ServerWebExchange> predicate = new AnyPathRoutePredicateFactory()
				.apply(new Config().setPatterns("/path", "/path/extra/**"));

		assertThat(predicate.test(exchange)).isTrue();
	}

	@Test
	public void regexPathMatch() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com/path/extra/index/example.html")
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		Predicate<ServerWebExchange> predicate = new AnyPathRoutePredicateFactory()
				.apply(new Config().setPatterns("/path", "/path/extra/**"));

		assertThat(predicate.test(exchange)).isTrue();
	}
}
