/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.junit.Test;

import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void noCookiesForYou() {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://example.com")
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		Predicate<ServerWebExchange> predicate = new CookieRoutePredicateFactory()
				.apply(new Config().setName("mycookie").setRegexp("ch.p"));

		assertThat(predicate.test(exchange)).isFalse();
	}

	@Test
	public void okOneCookieForYou() {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://example.com")
				.cookie(new HttpCookie("yourcookie", "sugar"),
						new HttpCookie("mycookie", "chip"))
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		Predicate<ServerWebExchange> predicate = new CookieRoutePredicateFactory()
				.apply(new Config().setName("mycookie").setRegexp("ch.p"));

		assertThat(predicate.test(exchange)).isTrue();
	}

}
