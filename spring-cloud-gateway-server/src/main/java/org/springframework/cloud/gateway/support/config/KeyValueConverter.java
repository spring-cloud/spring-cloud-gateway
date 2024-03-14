/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.support.config;

import org.springframework.core.convert.converter.Converter;

/**
 * @author Marta Medio
 */
public class KeyValueConverter implements Converter<String, KeyValue> {

	private static final String INVALID_CONFIGURATION_MESSAGE = "Invalid configuration, expected format is: 'key:value', received: ";

	@Override
	public KeyValue convert(String source) throws IllegalArgumentException {
		try {
			String[] split = source.split(":");
			if (split.length == 2) {
				return new KeyValue(split[0], split.length == 1 ? "" : split[1]);
			}
			throw new IllegalArgumentException(INVALID_CONFIGURATION_MESSAGE + source);
		}
		catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException(INVALID_CONFIGURATION_MESSAGE + source);
		}
	}

}
