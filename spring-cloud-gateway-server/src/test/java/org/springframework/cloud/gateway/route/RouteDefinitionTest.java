/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import java.util.Map;

import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Stefan Stus
 */
public class RouteDefinitionTest {

	@Test
	public void addRouteDefinitionKeepsExistingMetadata() {
		Map<String, Object> originalMetadata = Maps.newHashMap("key", "value");
		Map<String, Object> newMetadata = Maps.newHashMap("key2", "value2");

		RouteDefinition routeDefinition = new RouteDefinition();
		routeDefinition.setMetadata(originalMetadata);
		routeDefinition.getMetadata().putAll(newMetadata);

		assertThat(routeDefinition.getMetadata()).hasSize(2).containsAllEntriesOf(originalMetadata)
				.containsAllEntriesOf(newMetadata);
	}

	@Test
	public void setRouteDefinitionReplacesExistingMetadata() {
		Map<String, Object> originalMetadata = Maps.newHashMap("key", "value");
		Map<String, Object> newMetadata = Maps.newHashMap("key2", "value2");

		RouteDefinition routeDefinition = new RouteDefinition();
		routeDefinition.setMetadata(originalMetadata);
		routeDefinition.setMetadata(newMetadata);

		assertThat(routeDefinition.getMetadata()).isEqualTo(newMetadata);
	}

	@Test
	public void addSingleMetadataEntryKeepsOriginalMetadata() {
		Map<String, Object> originalMetadata = Maps.newHashMap("key", "value");

		RouteDefinition routeDefinition = new RouteDefinition();
		routeDefinition.setMetadata(originalMetadata);
		routeDefinition.getMetadata().put("key2", "value2");

		assertThat(routeDefinition.getMetadata()).hasSize(2).containsAllEntriesOf(originalMetadata)
				.containsEntry("key2", "value2");
	}

}
