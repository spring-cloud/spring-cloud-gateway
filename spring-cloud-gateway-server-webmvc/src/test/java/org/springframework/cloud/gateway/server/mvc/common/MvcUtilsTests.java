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

package org.springframework.cloud.gateway.server.mvc.common;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Garvit Joshi
 */
class MvcUtilsTests {

	@Test
	void stripContextPathRemovesPrefix() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/context/path")
			.buildRequest(null);
		servletRequest.setContextPath("/context");

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		assertThat(MvcUtils.stripContextPath(request, "/context/path")).isEqualTo("/path");
	}

	@Test
	void stripContextPathReturnsSlashWhenOnlyContextPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/context")
			.buildRequest(null);
		servletRequest.setContextPath("/context");

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		assertThat(MvcUtils.stripContextPath(request, "/context")).isEqualTo("/");
	}

	@Test
	void stripContextPathNoOpWithoutContextPath() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/path").buildRequest(null);

		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		assertThat(MvcUtils.stripContextPath(request, "/path")).isEqualTo("/path");
	}

}
