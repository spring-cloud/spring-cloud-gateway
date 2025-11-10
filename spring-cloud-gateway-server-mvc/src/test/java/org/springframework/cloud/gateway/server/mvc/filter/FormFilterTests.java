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

import static org.assertj.core.api.Assertions.assertThat;
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
		assertThat(servletRequest.getParameter("queryArg1")).isEqualTo("foo");
		assertThat(servletRequest.getParameterValues("queryArg1")).containsExactly("foo");
		// "你好" is hello in Chinese
		assertThat(servletRequest.getParameterValues("queryArg2")).containsExactly("你好");
		assertThat(servletRequest.getParameter("queryArg2")).isEqualTo("你好");
		assertThat(servletRequest.getParameter("formArg1")).isNull();
		assertThat(servletRequest.getParameter("formArg2")).isNull();
		assertThat(servletRequest.getParameterMap().size()).isEqualTo(2);
		assertThat(toList(servletRequest.getParameterNames())).containsExactly("queryArg1", "queryArg2");
		assertThat(servletRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
		MultiValueMap<String, String> form = readForm(servletRequest);
		assertThat(form.size()).isEqualTo(2);
		assertThat(form.get("formArg1")).containsExactly("bar");
		assertThat(form.get("formArg2")).containsExactly("{}");
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
