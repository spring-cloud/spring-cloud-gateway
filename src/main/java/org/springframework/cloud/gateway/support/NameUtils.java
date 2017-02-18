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

import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicate;

/**
 * @author Spencer Gibb
 */
public class NameUtils {

	public static String normalizePredicateName(Class<? extends RoutePredicate> clazz) {
		return clazz.getSimpleName().replace(RoutePredicate.class.getSimpleName(), "");
	}

	public static String normalizeFilterName(Class<? extends RouteFilter> clazz) {
		return clazz.getSimpleName().replace(RouteFilter.class.getSimpleName(), "");
	}
}
