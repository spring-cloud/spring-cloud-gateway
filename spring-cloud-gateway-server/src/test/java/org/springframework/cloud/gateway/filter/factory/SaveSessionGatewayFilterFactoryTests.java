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

package org.springframework.cloud.gateway.filter.factory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Greg Turnquist
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "save-session-web-filter")
public class SaveSessionGatewayFilterFactoryTests extends BaseWebClientTests {

	static WebSession mockWebSession = mock(WebSession.class);

	private final Map<String, WebSession> fakeSessionStore = new ConcurrentHashMap<>();

	private static final String SESSION_ID = "RANDOM_SESSION_ID";

	@Test
	public void webCallShouldTriggerWebSessionSaveAction() {

		when(mockWebSession.getAttributes()).thenReturn(new HashMap<>());
		Mono<Void> doSaveSession = Mono.fromRunnable(() -> {
			// Do save session. Need to make sure the Mono is subscribed
			fakeSessionStore.put(SESSION_ID, mockWebSession);
		});
		when(mockWebSession.save()).thenReturn(doSaveSession);

		Mono<Map> result = webClient.get().uri("/get").retrieve().bodyToMono(Map.class);

		StepVerifier.create(result).consumeNextWith(response -> {
			// Don't care about data, just need to catch signal
		}).expectComplete().verify(Duration.ofMinutes(10));

		verify(mockWebSession).save();
		assertThat(fakeSessionStore.get(SESSION_ID)).isEqualTo(mockWebSession);
	}

	@Test
	public void toStringFormat() {
		GatewayFilter filter = new SaveSessionGatewayFilterFactory().apply("");
		assertThat(filter.toString()).contains("SaveSession");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@Bean
		WebSessionManager webSessionManager() {
			return exchange -> Mono.just(mockWebSession);
		}

	}

}
