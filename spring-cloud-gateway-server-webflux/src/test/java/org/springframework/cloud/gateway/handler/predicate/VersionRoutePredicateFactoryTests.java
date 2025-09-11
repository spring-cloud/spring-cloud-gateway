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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.accept.ApiVersionStrategy;
import org.springframework.web.reactive.accept.DefaultApiVersionStrategy;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionRoutePredicateFactoryTests {

	@Test
	void versionPredicateWorks() {
		VersionRoutePredicateFactory factory = new VersionRoutePredicateFactory(apiVersionStrategy());
		Predicate<ServerWebExchange> predicate = factory
			.apply(new VersionRoutePredicateFactory.Config().setVersion("1.1"));
		assertThat(predicate.test(exchange("1.1"))).isTrue();
		assertThat(predicate.test(exchange("1.5"))).isFalse();

		predicate = factory.apply(new VersionRoutePredicateFactory.Config().setVersion("1.1+"));
		assertThat(predicate.test(exchange("1.5"))).isTrue();
	}

	private static ServerWebExchange exchange(String version) {
		ApiVersionStrategy versionStrategy = apiVersionStrategy();
		Comparable<?> parsedVersion = versionStrategy.parseVersion(version);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://localhost"));
		exchange.getAttributes().put(HandlerMapping.API_VERSION_ATTRIBUTE, parsedVersion);
		return exchange;
	}

	static DefaultApiVersionStrategy apiVersionStrategy() {
		return new DefaultApiVersionStrategy(List.of(exchange -> null), new SemanticApiVersionParser(), true, null,
				false, null, null);
	}

}
