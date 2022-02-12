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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * Creates a predicate which indicates if the request is intended for a Cloud Foundry
 * Route Service.
 *
 * @author Andrew Fitzgerald
 * @see <a href="https://docs.cloudfoundry.org/services/route-services.html">Cloud Foundry
 * Route Service documentation</a>
 */
public class CloudFoundryRouteServiceRoutePredicateFactory extends AbstractRoutePredicateFactory<Object> {

	/**
	 * Forwarded URL header name.
	 */
	public static final String X_CF_FORWARDED_URL = "X-CF-Forwarded-Url";

	/**
	 * Proxy signature header name.
	 */
	public static final String X_CF_PROXY_SIGNATURE = "X-CF-Proxy-Signature";

	/**
	 * Proxy metadata header name.
	 */
	public static final String X_CF_PROXY_METADATA = "X-CF-Proxy-Metadata";

	private final HeaderRoutePredicateFactory factory = new HeaderRoutePredicateFactory();

	public CloudFoundryRouteServiceRoutePredicateFactory() {
		super(Object.class);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Object unused) {
		return headerPredicate(X_CF_FORWARDED_URL).and(headerPredicate(X_CF_PROXY_SIGNATURE))
				.and(headerPredicate(X_CF_PROXY_METADATA));
	}

	private Predicate<ServerWebExchange> headerPredicate(String header) {
		HeaderRoutePredicateFactory.Config config = factory.newConfig();
		config.setHeader(header);
		config.setRegexp(".*");
		return factory.apply(config);
	}

}
