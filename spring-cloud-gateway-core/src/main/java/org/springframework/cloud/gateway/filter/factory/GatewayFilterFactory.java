/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.support.ArgumentHints;
import org.springframework.cloud.gateway.support.GatewayServerHttpRequestBuilder;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;

/**
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface GatewayFilterFactory extends ArgumentHints {

	String NAME_KEY = "name";
	String VALUE_KEY = "value";

	GatewayFilter apply(Tuple args);

	default String name() {
		return NameUtils.normalizeFilterName(getClass());
	}


	default ServerHttpRequest.Builder mutate(ServerHttpRequest request) {
		return new GatewayServerHttpRequestBuilder(request);
	}
}
