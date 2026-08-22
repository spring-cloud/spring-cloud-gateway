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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Aryamann Singh
 */
public class RequestRateLimiterPropertiesTests {

	@Test
	public void defaultsAreUnchanged() {
		RequestRateLimiterProperties properties = new RequestRateLimiterProperties();

		assertThat(properties.isDenyEmptyKey()).isTrue();
		assertThat(properties.getEmptyKeyStatusCode()).isEqualTo(HttpStatus.FORBIDDEN.name());
		assertThat(properties.isThrowOnLimit()).isFalse();
	}

	@Test
	public void factoryExposesInjectedProperties() {
		RequestRateLimiterProperties properties = new RequestRateLimiterProperties();
		RequestRateLimiterGatewayFilterFactory factory = new RequestRateLimiterGatewayFilterFactory(
				mock(RateLimiter.class), mock(KeyResolver.class), properties);

		assertThat(factory.getProperties()).isSameAs(properties);
	}

	@Test
	public void deprecatedAccessorsDelegateToProperties() {
		RequestRateLimiterGatewayFilterFactory factory = new RequestRateLimiterGatewayFilterFactory(
				mock(RateLimiter.class), mock(KeyResolver.class));

		factory.setDenyEmptyKey(false);
		factory.setEmptyKeyStatusCode(HttpStatus.BAD_REQUEST.name());
		factory.setThrowOnLimit(true);

		assertThat(factory.getProperties().isDenyEmptyKey()).isFalse();
		assertThat(factory.getProperties().getEmptyKeyStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.name());
		assertThat(factory.getProperties().isThrowOnLimit()).isTrue();

		// read-through via the deprecated getters
		assertThat(factory.isDenyEmptyKey()).isFalse();
		assertThat(factory.isThrowOnLimit()).isTrue();
	}

}
