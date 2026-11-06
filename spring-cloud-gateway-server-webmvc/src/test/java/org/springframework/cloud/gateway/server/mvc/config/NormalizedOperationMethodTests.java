/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.config;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.common.NameUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut.Type;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.DefaultOperationMethod;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Burak Kalayci
 */
class NormalizedOperationMethodTests {

	@Test
	void listTailFlagWithoutTrailingBooleanKeepsFieldsAndDefaultsFlagFalse() throws Exception {
		Method method = SampleFilters.class.getDeclaredMethod("removeJsonAttributesResponseBody", List.class,
				boolean.class);
		Map<String, Object> args = new LinkedHashMap<>();
		args.put(NameUtils.generateName(0), "id");
		args.put(NameUtils.generateName(1), "color");

		NormalizedOperationMethod operationMethod = new NormalizedOperationMethod(new DefaultOperationMethod(method),
				args);
		Map<String, Object> normalized = operationMethod.getNormalizedArgs();

		assertThat(normalized).containsOnlyKeys("fieldList", "deleteRecursively");
		assertThat(normalized.get("fieldList")).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Object> fieldList = (List<Object>) normalized.get("fieldList");
		assertThat(fieldList).containsExactly("id", "color");
		assertThat(normalized.get("deleteRecursively")).isEqualTo(Boolean.FALSE);
	}

	@Test
	void listTailFlagWithTrailingBooleanStripsFlagFromFieldList() throws Exception {
		Method method = SampleFilters.class.getDeclaredMethod("removeJsonAttributesResponseBody", List.class,
				boolean.class);
		Map<String, Object> args = new LinkedHashMap<>();
		args.put(NameUtils.generateName(0), "id");
		args.put(NameUtils.generateName(1), "color");
		args.put(NameUtils.generateName(2), "true");

		NormalizedOperationMethod operationMethod = new NormalizedOperationMethod(new DefaultOperationMethod(method),
				args);
		Map<String, Object> normalized = operationMethod.getNormalizedArgs();

		@SuppressWarnings("unchecked")
		List<Object> fieldList = (List<Object>) normalized.get("fieldList");
		assertThat(fieldList).containsExactly("id", "color");
		assertThat(normalized.get("deleteRecursively")).isEqualTo(Boolean.TRUE);
	}

	@Test
	void listTailFlagIgnoresNonBooleanTrailingValue() throws Exception {
		Method method = SampleFilters.class.getDeclaredMethod("removeJsonAttributesResponseBody", List.class,
				boolean.class);
		Map<String, Object> args = new LinkedHashMap<>();
		args.put(NameUtils.generateName(0), "id");
		args.put(NameUtils.generateName(1), "notAFlag");

		NormalizedOperationMethod operationMethod = new NormalizedOperationMethod(new DefaultOperationMethod(method),
				args);
		Map<String, Object> normalized = operationMethod.getNormalizedArgs();

		@SuppressWarnings("unchecked")
		List<Object> fieldList = (List<Object>) normalized.get("fieldList");
		assertThat(fieldList).containsExactly("id", "notAFlag");
		assertThat(normalized.get("deleteRecursively")).isEqualTo(Boolean.FALSE);
	}

	static final class SampleFilters {

		@Shortcut(type = Type.LIST_TAIL_FLAG, fieldOrder = { "fieldList", "deleteRecursively" })
		static HandlerFilterFunction<ServerResponse, ServerResponse> removeJsonAttributesResponseBody(
				List<String> fieldList, boolean deleteRecursively) {
			return (request, next) -> next.handle(request);
		}

	}

}
