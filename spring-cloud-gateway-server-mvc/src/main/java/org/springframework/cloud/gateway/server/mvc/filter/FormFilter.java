/*
 * Copyright 2013-2020 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Filter that rebuilds the body for form urlencoded posts. Serlvets treat query
 * parameters and form parameters the same, but a proxy should not care.
 *
 * @author Spencer Gibb
 */
@SuppressWarnings("unchecked")
public class FormFilter implements Filter, Ordered {

	/**
	 * Order of Form filter. Before WeightCalculatorFilter
	 */
	public static final int FORM_FILTER_ORDER = WeightCalculatorFilter.WEIGHT_CALC_FILTER_ORDER - 100;

	private int order = FORM_FILTER_ORDER;

	public FormFilter() {

	}

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;

		if (isFormPost(req)) {
			HttpServletRequest wrapped = getRequestWithBodyFromRequestParameters(req);
			chain.doFilter(wrapped, response);
		}
		else {
			chain.doFilter(request, response);
		}
	}

	private static final Charset FORM_CHARSET = StandardCharsets.UTF_8;

	static boolean isFormPost(HttpServletRequest request) {
		String contentType = request.getContentType();
		return (contentType != null && contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
				&& HttpMethod.POST.matches(request.getMethod()));
	}

	/**
	 * Use {@link ServletRequest#getParameterMap()} to reconstruct the body of a form
	 * 'POST' providing a predictable outcome as opposed to reading from the body, which
	 * can fail if any other code has used the ServletRequest to access a parameter, thus
	 * causing the input stream to be "consumed".
	 */
	static HttpServletRequest getRequestWithBodyFromRequestParameters(HttpServletRequest request) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
		Writer writer = new OutputStreamWriter(bos, FORM_CHARSET);

		Map<String, String[]> form = request.getParameterMap();
		String queryString = request.getQueryString();
		StringBuffer requestURL = request.getRequestURL();
		if (StringUtils.hasText(queryString)) {
			requestURL.append('?').append(queryString);
		}
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(requestURL.toString());
		MultiValueMap<String, String> queryParams = uriComponentsBuilder.build().getQueryParams();
		for (Iterator<Map.Entry<String, String[]>> entryIterator = form.entrySet().iterator(); entryIterator
			.hasNext();) {
			Map.Entry<String, String[]> entry = entryIterator.next();
			String name = entry.getKey();
			List<String> values = Arrays.asList(entry.getValue());
			for (Iterator<String> valueIterator = values.iterator(); valueIterator.hasNext();) {
				String value = valueIterator.next();
				List<String> queryValues = queryParams.get(name);
				boolean isQueryParam = queryParams.containsKey(name) && queryValues != null
						&& queryValues.contains(value);
				if (!isQueryParam) {
					writer.write(URLEncoder.encode(name, FORM_CHARSET));
					if (value != null) {
						writer.write('=');
						writer.write(URLEncoder.encode(value, FORM_CHARSET));
						if (valueIterator.hasNext()) {
							writer.write('&');
						}
					}
				}
			}
			if (entryIterator.hasNext()) {
				writer.append('&');
			}
		}
		writer.flush();

		ByteArrayServletInputStream servletInputStream = new ByteArrayServletInputStream(
				new ByteArrayInputStream(bos.toByteArray()));
		return new FormContentRequestWrapper(request, queryParams) {
			@Override
			public ServletInputStream getInputStream() throws IOException {
				return servletInputStream;
			}
		};
	}

	private static class ByteArrayServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream body;

		ByteArrayServletInputStream(ByteArrayInputStream body) {
			body.reset();
			this.body = body;
		}

		@Override
		public boolean isFinished() {
			return body.available() <= 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener listener) {

		}

		@Override
		public int read() {
			return body.read();
		}

	}

	private static class FormContentRequestWrapper extends HttpServletRequestWrapper {

		private final MultiValueMap<String, String> queryParams;

		FormContentRequestWrapper(HttpServletRequest request, MultiValueMap<String, String> params) {
			super(request);
			this.queryParams = params;
		}

		@Override
		@Nullable
		public String getParameter(String name) {
			return this.queryParams.getFirst(name);
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			Map<String, String[]> result = new LinkedHashMap<>();
			Enumeration<String> names = getParameterNames();
			while (names.hasMoreElements()) {
				String name = names.nextElement();
				result.put(name, getParameterValues(name));
			}
			return result;
		}

		@Override
		public Enumeration<String> getParameterNames() {
			return Collections.enumeration(this.queryParams.keySet());
		}

		@Override
		@Nullable
		public String[] getParameterValues(String name) {
			return StringUtils.toStringArray(this.queryParams.get(name));
		}

	}

}
