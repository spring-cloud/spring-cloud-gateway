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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.MediaType;

/**
 * @author Marta Medio
 * @author raccoonback
 */
public class RemoveJsonAttributesResponseBodyGatewayFilterFactory extends
		AbstractGatewayFilterFactory<RemoveJsonAttributesResponseBodyGatewayFilterFactory.FieldListConfiguration> {

	public RemoveJsonAttributesResponseBodyGatewayFilterFactory(
			ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory) {
		this.modifyResponseBodyGatewayFilterFactory = modifyResponseBodyGatewayFilterFactory;
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST_TAIL_FLAG;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("fieldList", "deleteRecursively");
	}

	@Override
	public FieldListConfiguration newConfig() {
		return new FieldListConfiguration();
	}

	@Override
	public Class<FieldListConfiguration> getConfigClass() {
		return FieldListConfiguration.class;
	}

	@Override
	public GatewayFilter apply(FieldListConfiguration config) {
		ModifyResponseBodyGatewayFilterFactory.Config modifyResponseBodyConfig = new ModifyResponseBodyGatewayFilterFactory.Config();
		modifyResponseBodyConfig.setInClass(String.class);
		modifyResponseBodyConfig.setOutClass(String.class);

		RewriteFunction<String, String> rewriteFunction = (exchange, body) -> {
			if (MediaType.APPLICATION_JSON.isCompatibleWith(exchange.getResponse().getHeaders().getContentType())) {
				try {
					JsonNode jsonNode = mapper.readValue(body, JsonNode.class);

					List<String> fieldList = Objects.requireNonNull(config.getFieldList(),
							"fieldList must not be null");
					removeJsonAttributes(jsonNode, fieldList, config.isDeleteRecursively());

					body = mapper.writeValueAsString(jsonNode);
				}
				catch (JacksonException e) {
					return Mono.error(new IllegalStateException("Failed to process JSON of response body.", e));
				}
			}
			return Mono.just(body);
		};
		modifyResponseBodyConfig.setRewriteFunction(rewriteFunction);

		return modifyResponseBodyGatewayFilterFactory.apply(modifyResponseBodyConfig);
	}

	private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory;

	private ObjectMapper mapper = new ObjectMapper();

	private void removeJsonAttributes(JsonNode jsonNode, List<String> fieldNames, boolean deleteRecursively) {
		if (jsonNode instanceof ObjectNode objectNode) {
			objectNode.remove(fieldNames);
		}
		if (deleteRecursively) {
			jsonNode.forEach(childNode -> removeJsonAttributes(childNode, fieldNames, true));
		}
	}

	public static class FieldListConfiguration {

		private @Nullable List<String> fieldList;

		private boolean deleteRecursively;

		public boolean isDeleteRecursively() {
			return deleteRecursively;
		}

		public FieldListConfiguration setDeleteRecursively(boolean deleteRecursively) {
			this.deleteRecursively = deleteRecursively;
			return this;
		}

		public @Nullable List<String> getFieldList() {
			return fieldList;
		}

		public FieldListConfiguration setFieldList(List<String> fieldList) {
			this.fieldList = fieldList;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("fieldList", fieldList)
				.append("deleteRecursively", deleteRecursively)
				.toString();
		}

	}

}
