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

import java.util.List;

import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;

class Bucket4jFilterFunctionsTests {

	@Test
	void emptyKeyUsesConfiguredStatus() throws Exception {
		WebApplicationContext context = mock(WebApplicationContext.class);
		AsyncProxyManager<?> proxyManager = mock();
		when(context.getBean(AsyncProxyManager.class)).thenReturn(proxyManager);
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/limited");
		servletRequest.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
		ServerRequest request = ServerRequest.create(servletRequest, List.of());
		HandlerFunction<ServerResponse> next = mock();

		assertThat(rateLimit(config -> config.setKeyResolver(req -> "")).filter(request, next).statusCode())
			.isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(rateLimit(config -> config.setKeyResolver(req -> " ").setEmptyKeyStatus(HttpStatus.UNAUTHORIZED))
			.filter(request, next)
			.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(proxyManager, next);
	}

}
