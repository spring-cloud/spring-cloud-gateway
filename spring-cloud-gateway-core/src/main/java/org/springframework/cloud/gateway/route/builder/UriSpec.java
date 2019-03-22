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

package org.springframework.cloud.gateway.route.builder;

import java.net.URI;

import org.springframework.cloud.gateway.route.Route;

/**
 * A specification to add a URI to a route.
 */
public class UriSpec {

	final Route.AsyncBuilder routeBuilder;

	final RouteLocatorBuilder.Builder builder;

	UriSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		this.routeBuilder = routeBuilder;
		this.builder = builder;
	}

	/**
	 * Set the URI for the route.
	 * @param uri the URI for the route
	 * @return a {@link Route.AsyncBuilder}
	 */
	public Route.AsyncBuilder uri(String uri) {
		return this.routeBuilder.uri(uri);
	}

	/**
	 * Set the URI for the route.
	 * @param uri the URI for the route.
	 * @return a {@link Route.AsyncBuilder}
	 */
	public Route.AsyncBuilder uri(URI uri) {
		return this.routeBuilder.uri(uri);
	}

	<T> T getBean(Class<T> type) {
		return this.builder.getContext().getBean(type);
	}

}
