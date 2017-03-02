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

package org.springframework.cloud.gateway.api;

import java.util.Arrays;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * @author Spencer Gibb
 */
public class FilterDefinition {
	@NotNull
	private String name;
	private String[] args;

	public FilterDefinition() {
	}

	public FilterDefinition(String text) {
		int eqIdx = text.indexOf("=");
		if (eqIdx <= 0) {
			setName(text);
			return;
		}
		setName(text.substring(0, eqIdx));

		String[] args = tokenizeToStringArray(text.substring(eqIdx+1), ",");

		setArgs(args);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String... args) {
		this.args = args;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FilterDefinition that = (FilterDefinition) o;
		return Objects.equals(name, that.name) &&
				Arrays.equals(args, that.args);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, args);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("FilterDefinition{");
		sb.append("name='").append(name).append('\'');
		sb.append(", args=").append(Arrays.toString(args));
		sb.append('}');
		return sb.toString();
	}
}
