/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.invoke;

import java.lang.annotation.Annotation;

/**
 * A single operation parameter.
 *
 * @author Phillip Webb
 * @author Moritz Halbritter
 * @since 2.0.0
 */
public interface OperationParameter {

	/**
	 * Returns the parameter name.
	 * @return the name
	 */
	String getName();

	/**
	 * Returns the parameter type.
	 * @return the type
	 */
	Class<?> getType();

	/**
	 * Return if the parameter is mandatory (does not accept null values).
	 * @return if the parameter is mandatory
	 */
	boolean isMandatory();

	/**
	 * Returns this element's annotation for the specified type if such an annotation is
	 * present, else null.
	 * @param annotation class of the annotation
	 * @return annotation value
	 * @param <T> type of the annotation
	 * @since 2.7.8
	 */
	<T extends Annotation> T getAnnotation(Class<T> annotation);

}
