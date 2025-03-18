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
 * @author Jens Mallien
 */
class BeforeFilterFunctionsTests {

	@Test
	void setPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/legacy/path")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.setPath("/new/path").apply(request);

		assertThat(result.uri().toString()).hasToString("http://localhost/new/path");
	}

	@Test
	void setEncodedPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/legacy/path")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.setPath("/new/é").apply(request);

		assertThat(result.uri().toString()).hasToString("http://localhost/new/%C3%A9");
	}

	@Test
	void setPathWithParameters() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/legacy/path")
			.queryParam("foo", "bar")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.setPath("/new/path").apply(request);

		assertThat(result.uri().toString()).hasToString("http://localhost/new/path?foo=bar");
	}

	@Test
	void setPathWithEncodedParameters() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/legacy/path")
			.queryParam("foo[]", "bar[]")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.setPath("/new/path").apply(request);

		assertThat(result.uri().toString()).hasToString("http://localhost/new/path?foo%5B%5D=bar%5B%5D");
	}

	@Test
	void removeRequestParameter() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.queryParam("foo", "bar")
			.queryParam("baz", "qux")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.removeRequestParameter("foo").apply(request);

		assertThat(result.param("foo")).isEmpty();
		assertThat(result.param("baz")).isPresent().hasValue("qux");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz=qux");
	}

	@Test
	void removeEncodedRequestParameter() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.queryParam("foo[]", "bar")
			.queryParam("baz", "qux")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.removeRequestParameter("foo[]").apply(request);

		assertThat(result.param("foo[]")).isEmpty();
		assertThat(result.param("baz")).isPresent().hasValue("qux");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz=qux");
	}

	@Test
	void removeRequestParameterWithEncodedRemainParameters() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path")
			.queryParam("foo", "bar")
			.queryParam("baz[]", "qux[]")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.removeRequestParameter("foo").apply(request);

		assertThat(result.param("foo")).isEmpty();
		assertThat(result.param("baz[]")).isPresent().hasValue("qux[]");
		assertThat(result.uri().toString()).hasToString("http://localhost/path?baz%5B%5D=qux%5B%5D");
	}

	@Test
	void removeRequestParameterWithEncodedPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/é")
			.queryParam("foo", "bar")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.removeRequestParameter("foo").apply(request);

		assertThat(result.param("foo")).isEmpty();
		assertThat(result.uri().toString()).hasToString("http://localhost/%C3%A9");
	}

	@Test
	void stripPrefix() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/depth1/depth2/depth3")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.stripPrefix(2).apply(request);

		assertThat(result.uri().toString()).hasToString("http://localhost/depth3");
	}

	@Test
	void stripPrefixWithEncodedPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/depth1/depth2/depth3/é")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.stripPrefix(2).apply(request);

		assertThat(result.uri().toString()).hasToString("http://localhost/depth3/%C3%A9");
	}

	@Test
	void stripPrefixWithEncodedParameters() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/depth1/depth2/depth3")
			.queryParam("baz[]", "qux[]")
			.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest result = BeforeFilterFunctions.stripPrefix(2).apply(request);

		assertThat(result.param("baz[]")).isPresent().hasValue("qux[]");
		assertThat(result.uri().toString()).hasToString("http://localhost/depth3?baz%5B%5D=qux%5B%5D");
	}

	@Test
	void rewritePath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
				.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest modified = BeforeFilterFunctions.rewritePath("get", "modified").apply(request);

		assertThat(modified.uri().getRawPath()).isEqualTo("/modified");
	}

	@Test
	void rewritePathWithSpace() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get/path/with spaces")
				.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest modified = BeforeFilterFunctions.rewritePath("get", "modified").apply(request);

		assertThat(modified.uri().getRawPath()).isEqualTo("/modified/path/with%20spaces");
	}

	@Test
	void rewritePathWithEnDash() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get/path/with–en–dashes")
				.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest modified = BeforeFilterFunctions.rewritePath("get", "modified").apply(request);

		assertThat(modified.uri().getRawPath()).isEqualTo("/modified/path/with%E2%80%93en%E2%80%93dashes");
	}

	@Test
	void rewritePathWithEnDashAndSpace() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get/path/with–en–dashes and spaces")
				.buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		ServerRequest modified = BeforeFilterFunctions.rewritePath("get", "modified").apply(request);

		assertThat(modified.uri().getRawPath()).isEqualTo("/modified/path/with%E2%80%93en%E2%80%93dashes%20and%20spaces");
	}

}
