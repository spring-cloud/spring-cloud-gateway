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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Externalized configuration for {@link RequestRateLimiterGatewayFilterFactory}.
 * Extracting the bound properties into a dedicated class with a default constructor lets
 * Spring Cloud's {@code ConfigurationPropertiesRebinder} rebind them on a refresh
 * (including resetting removed properties to their defaults), which is not possible while
 * the properties live on the {@link RequestRateLimiterGatewayFilterFactory} bean whose
 * {@code RateLimiter}/{@code KeyResolver} dependencies are constructor injected.
 *
 * @author Aryamann Singh
 */
@ConfigurationProperties("spring.cloud.gateway.server.webflux.filter.request-rate-limiter")
public class RequestRateLimiterProperties {

	/**
	 * Switch to deny requests if the Key Resolver returns an empty key, defaults to true.
	 */
	private boolean denyEmptyKey = true;

	/** HttpStatus to return when denyEmptyKey is true, defaults to FORBIDDEN. */
	private String emptyKeyStatusCode = HttpStatus.FORBIDDEN.name();

	/**
	 * Switch to throw a {@link HttpClientErrorException} when the request is denied by
	 * the RateLimiter, defaults to false.
	 */
	private boolean throwOnLimit = false;

	public boolean isDenyEmptyKey() {
		return denyEmptyKey;
	}

	public void setDenyEmptyKey(boolean denyEmptyKey) {
		this.denyEmptyKey = denyEmptyKey;
	}

	public String getEmptyKeyStatusCode() {
		return emptyKeyStatusCode;
	}

	public void setEmptyKeyStatusCode(String emptyKeyStatusCode) {
		this.emptyKeyStatusCode = emptyKeyStatusCode;
	}

	public boolean isThrowOnLimit() {
		return throwOnLimit;
	}

	public void setThrowOnLimit(boolean throwOnLimit) {
		this.throwOnLimit = throwOnLimit;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("denyEmptyKey", denyEmptyKey)
			.append("emptyKeyStatusCode", emptyKeyStatusCode)
			.append("throwOnLimit", throwOnLimit)
			.toString();
	}

}
