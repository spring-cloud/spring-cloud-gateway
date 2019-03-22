/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.registry.RegistrySocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicateFilter;
import org.springframework.core.OrderComparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SocketAcceptorFilterOrderTests {

	@Test
	public void predicateFilterAfterRegistryFilter() {
		SocketAcceptorFilter predicateFilter = new SocketAcceptorPredicateFilter(
				Collections.emptyList());
		SocketAcceptorFilter registryFilter = new RegistrySocketAcceptorFilter(
				mock(Registry.class));
		List<SocketAcceptorFilter> filters = Arrays.asList(predicateFilter,
				registryFilter);
		OrderComparator.sort(filters);

		assertThat(filters).containsExactly(registryFilter, predicateFilter);
	}

}
