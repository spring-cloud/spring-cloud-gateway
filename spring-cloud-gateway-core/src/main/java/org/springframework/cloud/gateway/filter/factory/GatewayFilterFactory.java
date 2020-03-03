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

package org.springframework.cloud.gateway.filter.factory;

import java.util.function.Consumer;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.support.Configurable;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.cloud.gateway.support.ShortcutConfigurable;

/**
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface GatewayFilterFactory<C> extends ShortcutConfigurable, Configurable<C> {

	/**
	 * Name key.
	 */
	String NAME_KEY = "name";

	/**
	 * Value key.
	 */
	String VALUE_KEY = "value";

	// useful for javadsl
	default GatewayFilter apply(String routeId, Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		return apply(routeId, config);
	}

	default GatewayFilter apply(Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		return apply(config);
	}

	default Class<C> getConfigClass() {
		throw new UnsupportedOperationException("getConfigClass() not implemented");
	}

	@Override
	default C newConfig() {
		throw new UnsupportedOperationException("newConfig() not implemented");
	}

	GatewayFilter apply(C config);

	default GatewayFilter apply(String routeId, C config) {
		if (config instanceof HasRouteId) {
			HasRouteId hasRouteId = (HasRouteId) config;
			hasRouteId.setRouteId(routeId);
		}
		return apply(config);
	}

	default String name() {
		// TODO: deal with proxys
		return NameUtils.normalizeFilterFactoryName(getClass());
	}

}
