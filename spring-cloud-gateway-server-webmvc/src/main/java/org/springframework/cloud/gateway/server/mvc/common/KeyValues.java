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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class KeyValues {

	private List<KeyValue> keyValues = new ArrayList<>();

	public List<KeyValue> getKeyValues() {
		return keyValues;
	}

	public void setKeyValues(List<KeyValue> keyValues) {
		this.keyValues = keyValues;
	}

	public static KeyValues valueOf(String s) {
		String[] tokens = StringUtils.tokenizeToStringArray(s, ",", true, true);
		List<KeyValue> parsedKeyValues = Arrays.stream(tokens).map(KeyValue::valueOf).toList();
		KeyValues keyValues = new KeyValues();
		keyValues.setKeyValues(parsedKeyValues);
		return keyValues;
	}

	public static class KeyValue {

		private final String key;

		private final String value;

		public KeyValue(String key, String value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("name", key).append("value", value).toString();
		}

		public static KeyValue valueOf(String s) {
			String[] tokens = StringUtils.tokenizeToStringArray(s, ":", true, true);
			Assert.isTrue(tokens.length == 2, () -> "String must be two tokens delimited by colon, but was " + s);
			return new KeyValue(tokens[0], tokens[1]);
		}

	}

}
