/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Indexed;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
public @interface Shortcut {

	/**
	 * The names and order of arguments for a shortcut. Synonym for {@link #fieldOrder()}.
	 * Defaults to the names and order of the method on which this is declared.
	 */
	@AliasFor("fieldOrder")
	String[] value() default {};

	/**
	 * The names and order of arguments for a shortcut. Synonym for {@link #value()}.
	 * Defaults to the names and order of the method on which this is declared.
	 */
	@AliasFor("value")
	String[] fieldOrder() default {};

	/**
	 * Strategy for parsing the shortcut String.
	 */
	Type type() default Type.DEFAULT;

	/**
	 * Optional property prefix to be appended to fields.
	 */
	String fieldPrefix() default "";

	enum Type {

		/**
		 * Default shortcut type.
		 */
		DEFAULT,

		/**
		 * List shortcut type.
		 */
		LIST,

		/**
		 * List is all elements except last which is a boolean flag.
		 */
		LIST_TAIL_FLAG;

	}

}
