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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.invoke.InvocationContext;
import org.springframework.cloud.gateway.server.mvc.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.OperationMethod;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.ReflectiveOperationInvoker;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestFilterSupplier;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.function.HandlerFilterFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FilterDiscoverer}.
 *
 * @author zephyr45
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class FilterDiscovererTests {

	@Test
	void contextLoads() {
		MultiValueMap<String, OperationMethod> operations = new FilterDiscoverer().getOperations();
		assertThat(operations).isNotEmpty();
		OperationMethod operation = Objects.requireNonNull(operations.getFirst("httpbinUriResolver"));
		assertThat(operation.getTarget()).isInstanceOf(TestFilterSupplier.class);
		HandlerFilterFunction<?, ?> filter = new ReflectiveOperationInvoker(operation,
				new ConversionServiceParameterValueMapper())
			.invoke(new InvocationContext(Map.of()));
		assertThat(filter).isInstanceOf(HttpbinUriResolver.class);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Config {

	}

}
