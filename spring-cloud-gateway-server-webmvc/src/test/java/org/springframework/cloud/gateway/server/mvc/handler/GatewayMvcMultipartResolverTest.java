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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class GatewayMvcMultipartResolverTest {
	private MockHttpServletRequest mockRequest;

	@BeforeEach
	public void setUp() {
		mockRequest = new MockHttpServletRequest();
		mockRequest.setContentType("multipart/form-data; boundary=----boundary");
	}

	private GatewayMvcMultipartResolver.GatewayMultipartHttpServletRequest buildWrapper() {
		return new GatewayMvcMultipartResolver.GatewayMultipartHttpServletRequest(mockRequest);
	}

	private void makeGatewayRequest() {
		mockRequest.setAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "my-route");
	}

	@Test
	public void getParameterMapGatewayRequestMultipleDistinctParams() {
		makeGatewayRequest();
		mockRequest.setQueryString("foo=bar&baz=qux");

		Map<String, String[]> result = buildWrapper().getParameterMap();

		assertThat(result).containsKeys("foo", "baz");
		assertThat(result.get("foo")).containsExactly("bar");
		assertThat(result.get("baz")).containsExactly("qux");
	}

	@Test
	public void getParameterMapGatewayRequestNullQueryStringReturnsEmptyMap() {
		makeGatewayRequest();
		mockRequest.setQueryString(null);

		Map<String, String[]> result = buildWrapper().getParameterMap();

		assertThat(result).isEmpty();
	}

	@Test
	public void getParameterMapGatewayRequestEmptyQueryStringReturnsEmptyMap() {
		makeGatewayRequest();
		mockRequest.setQueryString(StringUtils.EMPTY);

		Map<String, String[]> result = buildWrapper().getParameterMap();

		assertThat(result).isEmpty();
	}

	@Test
	public void getParameterMap_gatewayRequest_multipartBodyIsNotParsed() {
		makeGatewayRequest();

		String body = """
				------TestBoundary\r
				Content-Disposition: form-data; name="file"; filename="test.txt"\r
				Content-Type: text/plain\r
				\r
				file content here\r
				------TestBoundary\r
				Content-Disposition: form-data; name="field1"\r
				\r
				value1\r
				------TestBoundary--\r
				""";

		mockRequest.setContentType("multipart/form-data; boundary=----TestBoundary");
		mockRequest.setContent(body.getBytes(StandardCharsets.UTF_8));
		mockRequest.setQueryString("queryParam=fromQuery");

		GatewayMvcMultipartResolver.GatewayMultipartHttpServletRequest wrapper =
				spy(new GatewayMvcMultipartResolver.GatewayMultipartHttpServletRequest(mockRequest));

		Map<String, String[]> params = wrapper.getParameterMap();

		// multipart parsing isn't triggered
		verify(wrapper, never()).initializeMultipart();

		// Body parts must not appear — only query string should be visible
		assertThat(params).containsOnlyKeys("queryParam");
		assertThat(params.get("queryParam")).containsExactly("fromQuery");
		assertThat(params).doesNotContainKey("field1").doesNotContainKey("file");
	}

	@Test
	public void getParameterMapNonGatewayRequestNoAttributesDelegatesToSuper() {
		mockRequest.setParameter("superKey", "superValue");

		Map<String, String[]> result = buildWrapper().getParameterMap();

		assertThat(result).containsKey("superKey");
		assertThat(result.get("superKey")).containsExactly("superValue");
	}
}
