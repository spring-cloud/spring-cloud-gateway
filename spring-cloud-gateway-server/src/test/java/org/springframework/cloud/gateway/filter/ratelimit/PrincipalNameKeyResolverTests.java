/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.security.Principal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.web.server.ServerWebExchange;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrincipalNameKeyResolverTests {

	@Test
	public void nullPrincipalNameWorks() {
		PrincipalNameKeyResolver keyResolver = new PrincipalNameKeyResolver();
		ServerWebExchange exchange = mock(ServerWebExchange.class);
		when(exchange.getPrincipal()).thenReturn(Mono.just(mock(Principal.class)));
		StepVerifier.create(keyResolver.resolve(exchange)).expectComplete().verify(Duration.ofSeconds(5));
	}

}
