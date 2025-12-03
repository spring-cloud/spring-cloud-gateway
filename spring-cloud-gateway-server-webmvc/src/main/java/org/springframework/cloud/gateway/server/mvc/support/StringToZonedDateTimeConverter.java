/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.core.convert.converter.Converter;

/**
 * Converter for converting String to ZonedDateTime. Supports both ISO-8601 format and
 * epoch milliseconds.
 *
 * @author raccoonback
 */
public class StringToZonedDateTimeConverter implements Converter<String, ZonedDateTime> {

	@Override
	public ZonedDateTime convert(String source) {
		try {
			long epoch = Long.parseLong(source);
			return Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.ofTotalSeconds(0)).toZonedDateTime();
		}
		catch (NumberFormatException e) {
			// try ZonedDateTime instead
			return ZonedDateTime.parse(source);
		}
	}

}
