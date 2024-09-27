/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Mallien
 */
public class BeforeFilterFunctionsTests {

	@Test
	public void rewritePath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
				.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest modified = BeforeFilterFunctions.rewritePath("get", "modified").apply(request);

		assertThat(modified.uri().getRawPath()).isEqualTo("/modified");
	}

	@Test
	public void rewritePathWithSpace() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get/path/with spaces")
				.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest modified = BeforeFilterFunctions.rewritePath("get", "modified").apply(request);

		assertThat(modified.uri().getRawPath()).isEqualTo("/modified/path/with%20spaces");
	}

}
