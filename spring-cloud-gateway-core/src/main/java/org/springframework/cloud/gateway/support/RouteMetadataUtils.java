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

package org.springframework.cloud.gateway.support;

import java.util.Arrays;
import java.util.List;

public final class RouteMetadataUtils {

	/**
	 * Response timeout attribute name.
	 */
	public static final String RESPONSE_TIMEOUT_ATTR = "response-timeout";

	/**
	 * Connect timeout attribute name.
	 */
	public static final String CONNECT_TIMEOUT_ATTR = "connect-timeout";

	/**
	 * Attributes which are expected to have Integer values
	 */
	public static final List<String> INTEGER_VALUE_KEYS = Arrays.asList(RESPONSE_TIMEOUT_ATTR, CONNECT_TIMEOUT_ATTR);

	private RouteMetadataUtils() {
		throw new AssertionError("Must not instantiate utility class.");
	}

}
