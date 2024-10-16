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

package org.springframework.cloud.gateway.route;

import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.util.CollectionUtils;

/**
 * @author Spencer Gibb
 */
// TODO: rename to Routes?
public interface RouteLocator {

	Flux<Route> getRoutes();

	/**
	 * Gets routes whose {@link Route#getId()} matches with any of the ids passed by
	 * parameters. If an ID cannot be found, it will not return a route for that ID.
	 */
	default Flux<Route> getRoutesByMetadata(Map<String, Object> metadata) {
		return getRoutes().filter(route -> matchMetadata(route.getMetadata(), metadata));
	}

	static boolean matchMetadata(Map<String, Object> toCheck, Map<String, Object> expectedMetadata) {
		if (CollectionUtils.isEmpty(expectedMetadata)) {
			return true;
		}
		else {
			return toCheck != null && expectedMetadata.entrySet()
				.stream()
				.allMatch(keyValue -> toCheck.containsKey(keyValue.getKey())
						&& toCheck.get(keyValue.getKey()).equals(keyValue.getValue()));
		}
	}

}
