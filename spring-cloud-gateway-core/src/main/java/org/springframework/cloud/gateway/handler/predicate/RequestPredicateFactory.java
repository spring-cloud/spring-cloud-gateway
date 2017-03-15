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

import java.util.Collections;
import java.util.List;

import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.RequestPredicate;

/**
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface RequestPredicateFactory {

	RequestPredicate apply(Tuple args);

	default String name() {
		return NameUtils.normalizePredicateName(getClass());
	}

	/**
	 * Returns hints about the number of args and the order for shortcut parsing.
	 * @return
	 */
	default List<String> argNames() {
		return Collections.emptyList();
	}

	/**
	 * Auto validate supplied argument size against {@see #argNames} size and that an arg for each key exists.
	 * Useful for variable arg predicates.
	 * @return
	 */
	default boolean validateArgs() {
		return true;
	}

	default void validate(int minimumSize, Tuple args) {
		Assert.isTrue(args != null && args.size() >= minimumSize,
				"args must have at least "+ minimumSize +" entry(s)");
	}
}
