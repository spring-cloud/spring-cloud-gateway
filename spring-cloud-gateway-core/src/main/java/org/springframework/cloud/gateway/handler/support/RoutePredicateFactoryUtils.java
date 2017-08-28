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

package org.springframework.cloud.gateway.handler.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

/**
 * @author Spencer Gibb
 */
public class RoutePredicateFactoryUtils {
	private static final Log logger = LogFactory.getLog(RoutePredicateFactory.class);

	public static void traceMatch(String prefix, Object desired, Object actual, boolean match) {
		if (logger.isTraceEnabled()) {
			String message = String.format("%s \"%s\" %s against value \"%s\"",
					prefix, desired, match ? "matches" : "does not match", actual);
			logger.trace(message);
		}
	}
}
