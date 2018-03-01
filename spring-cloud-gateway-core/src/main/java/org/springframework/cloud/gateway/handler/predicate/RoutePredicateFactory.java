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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.Configurable;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.cloud.gateway.support.ShortcutConfigurable;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface RoutePredicateFactory<C> extends ShortcutConfigurable, Configurable<C> {
	String PATTERN_KEY = "pattern";

	@Deprecated //TODO: remove when apply(Tuple) is removed
	default boolean isConfigurable() {
		return false;
	}

	// useful for javadsl
	default Predicate<ServerWebExchange> apply(Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		beforeApply(config);
		return apply(config);
	}

	default void beforeApply(C config) {}

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
	default Predicate<ServerWebExchange> apply(C config) {
		throw new UnsupportedOperationException("apply(C config) not implemented");
	}

	@Deprecated
	Predicate<ServerWebExchange> apply(Tuple args);

	default String name() {
		return NameUtils.normalizeRoutePredicateName(getClass());
	}

}
