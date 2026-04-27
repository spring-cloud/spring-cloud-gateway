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

package org.springframework.cloud.gateway.discovery;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.expression.spel.SpelEvaluationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Failures are not always the outer or innermost throwable: Reactor may wrap the signal,
 * and Spring SpEL often wraps an {@link org.springframework.expression.AccessException}
 * inside {@link SpelEvaluationException}, so the assertion walks the
 * {@linkplain Throwable#getCause() cause chain}.
 */
public class DiscoveryClientRouteDefinitionLocatorServiceInstanceSpelTests {

	@Test
	public void urlExpressionSpelInstanceMethodCallThrowsSpelEvaluationException() {
		DefaultServiceInstance instance = new DefaultServiceInstance("my-service-1", "my-service", "before-spel-host",
				8080, false);

		ReactiveDiscoveryClient discoveryClient = mock(ReactiveDiscoveryClient.class);
		when(discoveryClient.getServices()).thenReturn(Flux.just("my-service"));
		when(discoveryClient.getInstances("my-service")).thenReturn(Flux.just(instance));

		DiscoveryLocatorProperties properties = new DiscoveryLocatorProperties();
		properties.setUrlExpression("setHost('after-spel-host') ?: ('lb://' + host)");

		DiscoveryClientRouteDefinitionLocator locator = new DiscoveryClientRouteDefinitionLocator(discoveryClient,
				properties);

		assertThatThrownBy(() -> locator.getRouteDefinitions().collectList().block())
			.satisfies(t -> assertThat(throwableChainContains(t, SpelEvaluationException.class))
				.as("SpEL should reject instance method calls; cause chain: %s", formatChain(t))
				.isTrue());

		assertThat(instance.getHost()).isEqualTo("before-spel-host");
	}

	@Test
	public void urlExpressionSpelWorksOnMetadata() {
		ServiceInstance instance = new ServiceInstance() {
			@Override
			public String getServiceId() {
				return "test";
			}

			@Override
			public String getHost() {
				return "myhost";
			}

			@Override
			public int getPort() {
				return 8080;
			}

			@Override
			public boolean isSecure() {
				return false;
			}

			@Override
			public URI getUri() {
				return URI.create("http://localhost:8080/my-service-1");
			}

			@Override
			public Map<String, String> getMetadata() {
				Map<String, String> metadata = new HashMap<>();
				metadata.put("override-host", "http://host-override");
				return metadata;
			}
		};

		ReactiveDiscoveryClient discoveryClient = mock(ReactiveDiscoveryClient.class);
		when(discoveryClient.getServices()).thenReturn(Flux.just("my-service"));
		when(discoveryClient.getInstances("my-service")).thenReturn(Flux.just(instance));

		DiscoveryLocatorProperties properties = new DiscoveryLocatorProperties();
		properties.setUrlExpression("metadata['override-host'] ?: host");

		DiscoveryClientRouteDefinitionLocator locator = new DiscoveryClientRouteDefinitionLocator(discoveryClient,
				properties);

		List<RouteDefinition> routeDefinitions = locator.getRouteDefinitions().collectList().block();
		assertThat(routeDefinitions).hasSize(1);
		assertThat(routeDefinitions.get(0).getUri().getHost()).isEqualTo("host-override");

	}

	private static boolean throwableChainContains(Throwable throwable, Class<?> type) {
		if (throwable == null) {
			return false;
		}
		if (type.isInstance(throwable)) {
			return true;
		}
		if (throwableChainContains(throwable.getCause(), type)) {
			return true;
		}
		for (Throwable suppressed : throwable.getSuppressed()) {
			if (throwableChainContains(suppressed, type)) {
				return true;
			}
		}
		return false;
	}

	private static String formatChain(Throwable throwable) {
		StringBuilder sb = new StringBuilder();
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			sb.append(current.getClass().getName()).append(": ").append(current.getMessage()).append(" -> ");
		}
		if (sb.length() >= 4) {
			sb.setLength(sb.length() - 4);
		}
		return sb.toString();
	}

}
