/*
 * Copyright 2013-2025 the original author or authors.
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
 * @author raccoonback
 */
class BeforeFilterFunctionsTests {

	@Test
	void rewriteRequestParameter() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.param("foo", "bar")
			.param("baz", "qux")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.rewriteRequestParameter("foo", "replacement").apply(request);

		assertThat(result.param("foo")).isPresent().hasValue("replacement");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz=qux&foo=replacement");
	}

	@Test
	void rewriteOnlyFirstRequestParameter() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.param("foo", "bar_1")
			.param("foo", "bar_2")
			.param("foo", "bar_3")
			.param("baz", "qux")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.rewriteRequestParameter("foo", "replacement").apply(request);

		assertThat(result.param("foo")).isPresent().hasValue("replacement");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz=qux&foo=replacement");
	}

	@Test
	void rewriteEncodedRequestParameter() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.param("foo[]", "bar")
			.param("baz", "qux")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.rewriteRequestParameter("foo[]", "replacement[]").apply(request);

		assertThat(result.param("foo[]")).isPresent().hasValue("replacement[]");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz=qux&foo%5B%5D=replacement%5B%5D");
	}

	@Test
	void rewriteRequestParameterWithEncodedRemainParameters() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.param("foo", "bar")
			.param("baz[]", "qux[]")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.rewriteRequestParameter("foo", "replacement").apply(request);

		assertThat(result.param("foo")).isPresent().hasValue("replacement");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz%5B%5D=qux%5B%5D&foo=replacement");
	}

	@Test
	void rewriteRequestParameterWithEncodedPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path/é/last")
			.param("foo", "bar")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.rewriteRequestParameter("foo", "replacement").apply(request);

		assertThat(result.param("foo")).isPresent().hasValue("replacement");
		assertThat(result.uri().toString()).hasToString("http://localhost/path/%C3%A9/last?foo=replacement");
	}

}
