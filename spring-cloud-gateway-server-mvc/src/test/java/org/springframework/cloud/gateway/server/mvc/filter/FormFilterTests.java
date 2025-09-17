/*
 * Copyright 2025-present the original author or authors.
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

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * @author shawyeok
 */
class FormFilterTests {

	@Test
	void hideFormParameterFromParameterMap() throws ServletException, IOException {
		FormFilter filter = new FormFilter();
		MockHttpServletRequest request = MockMvcRequestBuilders
				.post(URI.create("http://localhost/test?queryArg1=foo&queryArg2=%E4%BD%A0%E5%A5%BD"))
				.contentType("application/x-www-form-urlencoded")
				.content("formArg1=bar&formArg2=%7B%7D")
				.buildRequest(null);
		HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
		FilterChain chain = Mockito.mock(FilterChain.class);
		filter.doFilter(request, response, chain);

		ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
		verify(chain).doFilter(captor.capture(), Mockito.eq(response));
		HttpServletRequest servletRequest = (HttpServletRequest) captor.getValue();
		assertEquals("foo", servletRequest.getParameter("queryArg1"));
		assertArrayEquals(new String[]{"foo"}, servletRequest.getParameterValues("queryArg1"));
		// "你好" is hello in Chinese
		assertEquals("你好", servletRequest.getParameter("queryArg2"));
		assertArrayEquals(new String[]{"你好"}, servletRequest.getParameterValues("queryArg2"));
		assertNull(servletRequest.getParameter("formArg1"));
		assertNull(servletRequest.getParameter("formArg2"));
		assertEquals(2, servletRequest.getParameterMap().size());
		assertEquals(List.of("queryArg1", "queryArg2"), toList(servletRequest.getParameterNames()));
		assertEquals("application/x-www-form-urlencoded", servletRequest.getHeader("Content-Type"));
		MultiValueMap<String, String> form = readForm(servletRequest);
		assertEquals(2, form.size());
		assertEquals(List.of("bar"), form.get("formArg1"));
		assertEquals(List.of("{}"), form.get("formArg2"));
	}

	static MultiValueMap<String, String> readForm(HttpServletRequest request) {
		try {
			String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
			String[] pairs = StringUtils.tokenizeToStringArray(body, "&");
			MultiValueMap<String, String> result = new LinkedMultiValueMap<>(pairs.length);
			for (String pair : pairs) {
				int idx = pair.indexOf('=');
				if (idx == -1) {
					result.add(URLDecoder.decode(pair, StandardCharsets.UTF_8), null);
				}
				else {
					String name = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
					String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
					result.add(name, value);
				}
			}
			return result;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static List<String> toList(Enumeration<String> values) {
		List<String> list = new ArrayList<>();
		while (values.hasMoreElements()) {
			list.add(values.nextElement());
		}
		return list;
	}
}