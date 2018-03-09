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
import org.springframework.cloud.gateway.support.ShortcutConfigurable;
import org.springframework.cloud.gateway.support.Configurable;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;

import java.util.function.Consumer;

/**
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface GatewayFilterFactory<C> extends ShortcutConfigurable, Configurable<C> {

	String NAME_KEY = "name";
	String VALUE_KEY = "value";

	@Deprecated //TODO: remove when apply(Tuple) is removed
	default boolean isConfigurable() {
		return false;
	}

	// useful for javadsl
	default GatewayFilter apply(Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		return apply(config);
	}

	//TODO: remove after apply(Tuple) removed
	@Override
	default Class<C> getConfigClass() {
		throw new UnsupportedOperationException("getConfigClass() not implemented");
	}

	//TODO: remove after apply(Tuple) removed
	@Override
	default C newConfig() {
		throw new UnsupportedOperationException("newConfig() not implemented");
	}

	//TODO: remove default impl after apply(Tuple) removed
	default GatewayFilter apply(C config) {
		throw new UnsupportedOperationException("apply(C config) not implemented");
	}

	@Deprecated
	GatewayFilter apply(Tuple args);

	default String name() {
		//TODO: deal with proxys
		return NameUtils.normalizeFilterFactoryName(getClass());
	}

	@Deprecated
	default ServerHttpRequest.Builder mutate(ServerHttpRequest request) {
		return request.mutate();
	}
}
