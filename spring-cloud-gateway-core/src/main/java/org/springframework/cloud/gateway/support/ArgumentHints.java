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

package org.springframework.cloud.gateway.support;

import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;

import java.util.List;

/**
 * @author Spencer Gibb
 * @deprecated Use {@link ShortcutConfigurable} instead
 */
@Deprecated
public interface ArgumentHints extends ShortcutConfigurable {

	/**
	 * Returns hints about the number of args and the order for shortcut parsing.
	 * @return
	 */
	default List<String> argNames() {
		return shortcutFieldOrder();
	}

	/**
	 * Validate supplied argument size against {@see #argNames} size.
	 * Useful for variable arg predicates.
	 * @return
	 */
	default boolean validateArgs() {
		return validateFieldsExist();
	}

	@Deprecated
	default void validate(int requiredSize, Tuple args) {
		Assert.isTrue(args != null && args.size() == requiredSize,
				"args must have "+ requiredSize +" entry(s)");
	}

	@Deprecated
	default void validateMin(int minSize, Tuple args) {
		Assert.isTrue(args != null && args.size() >= minSize,
				"args must have at least "+ minSize +" entry(s)");
	}
}
