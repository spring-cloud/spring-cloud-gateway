/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.test;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

public class LocalHostUriBuilderFactory implements UriBuilderFactory {

	private static final String PREFIX = "server.servlet.";

	private final Environment environment;

	private final String scheme;

	private DefaultUriBuilderFactory.EncodingMode encodingMode = DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES;

	private final Map<String, Object> defaultUriVariables = new HashMap<>();

	private boolean parsePath = true;

	/**
	 * Create a new {@code LocalHostUriTemplateHandler} that will generate {@code http}
	 * URIs using the given {@code environment} to determine the context path and port.
	 * @param environment the environment used to determine the port
	 */
	public LocalHostUriBuilderFactory(Environment environment) {
		this(environment, "http");
	}

	/**
	 * Create a new {@code LocalHostUriTemplateHandler} that will generate URIs with the
	 * given {@code scheme} and use the given {@code environment} to determine the
	 * context-path and port.
	 * @param environment the environment used to determine the port
	 * @param scheme the scheme of the root uri
	 * @since 1.4.1
	 */
	public LocalHostUriBuilderFactory(Environment environment, String scheme) {
		Assert.notNull(environment, "Environment must not be null");
		Assert.notNull(scheme, "Scheme must not be null");
		this.environment = environment;
		this.scheme = scheme;
	}

	public String getPort() {
		return this.environment.getProperty("local.server.port", "8080");
	}

	public String getContextPath() {
		return this.environment.getProperty(PREFIX + "context-path", "");
	}

	public String getScheme() {
		return scheme;
	}

	/**
	 * Set the {@link DefaultUriBuilderFactory.EncodingMode encoding mode} to use.
	 * <p>
	 * By default this is set to
	 * {@link DefaultUriBuilderFactory.EncodingMode#TEMPLATE_AND_VALUES
	 * EncodingMode.TEMPLATE_AND_VALUES}.
	 * <p>
	 * <strong>Note:</strong> Prior to 5.1 the default was
	 * {@link DefaultUriBuilderFactory.EncodingMode#URI_COMPONENT
	 * EncodingMode.URI_COMPONENT} therefore the {@code WebClient} {@code RestTemplate}
	 * have switched their default behavior.
	 * @param encodingMode the encoding mode to use
	 */
	public void setEncodingMode(DefaultUriBuilderFactory.EncodingMode encodingMode) {
		this.encodingMode = encodingMode;
	}

	/**
	 * Return the configured encoding mode.
	 */
	public DefaultUriBuilderFactory.EncodingMode getEncodingMode() {
		return this.encodingMode;
	}

	/**
	 * Provide default URI variable values to use when expanding URI templates with a Map
	 * of variables.
	 * @param defaultUriVariables default URI variable values
	 */
	public void setDefaultUriVariables(@Nullable Map<String, ?> defaultUriVariables) {
		this.defaultUriVariables.clear();
		if (defaultUriVariables != null) {
			this.defaultUriVariables.putAll(defaultUriVariables);
		}
	}

	/**
	 * Return the configured default URI variable values.
	 */
	public Map<String, ?> getDefaultUriVariables() {
		return Collections.unmodifiableMap(this.defaultUriVariables);
	}

	/**
	 * Whether to parse the input path into path segments if the encoding mode is set to
	 * {@link DefaultUriBuilderFactory.EncodingMode#URI_COMPONENT
	 * EncodingMode.URI_COMPONENT}, which ensures that URI variables in the path are
	 * encoded according to path segment rules and for example a '/' is encoded.
	 * <p>
	 * By default this is set to {@code true}.
	 * @param parsePath whether to parse the path into path segments
	 */
	public void setParsePath(boolean parsePath) {
		this.parsePath = parsePath;
	}

	/**
	 * Whether to parse the path into path segments if the encoding mode is set to
	 * {@link DefaultUriBuilderFactory.EncodingMode#URI_COMPONENT
	 * EncodingMode.URI_COMPONENT}.
	 */
	public boolean shouldParsePath() {
		return this.parsePath;
	}

	@Override
	public UriBuilder uriString(String uriTemplate) {
		return new DefaultUriBuilder(uriTemplate);
	}

	@Override
	public UriBuilder builder() {
		return new DefaultUriBuilder("");
	}

	@Override
	public URI expand(String uriTemplate, Map<String, ?> uriVars) {
		return uriString(uriTemplate).build(uriVars);

	}

	@Override
	public URI expand(String uriTemplate, Object... uriVars) {
		return uriString(uriTemplate).build(uriVars);
	}

	/**
	 * {@link DefaultUriBuilderFactory} specific implementation of UriBuilder.
	 */
	private class DefaultUriBuilder implements UriBuilder {

		private final UriComponentsBuilder uriComponentsBuilder;

		DefaultUriBuilder(String uriTemplate) {
			this.uriComponentsBuilder = initUriComponentsBuilder(uriTemplate);
		}

		private UriComponentsBuilder initUriComponentsBuilder(String uriTemplate) {
			UriComponentsBuilder result = UriComponentsBuilder.fromUriString(uriTemplate);
			if (encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES)) {
				result.encode();
			}
			parsePathIfNecessary(result);
			return result;
		}

		private void parsePathIfNecessary(UriComponentsBuilder result) {
			if (parsePath && encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT)) {
				UriComponents uric = result.build();
				String path = uric.getPath();
				result.replacePath(null);
				for (String segment : uric.getPathSegments()) {
					result.pathSegment(segment);
				}
				if (path != null && path.endsWith("/")) {
					result.path("/");
				}
			}
		}

		@Override
		public DefaultUriBuilder scheme(@Nullable String scheme) {
			this.uriComponentsBuilder.scheme(scheme);
			return this;
		}

		@Override
		public DefaultUriBuilder userInfo(@Nullable String userInfo) {
			this.uriComponentsBuilder.userInfo(userInfo);
			return this;
		}

		@Override
		public DefaultUriBuilder host(@Nullable String host) {
			this.uriComponentsBuilder.host(host);
			return this;
		}

		@Override
		public DefaultUriBuilder port(int port) {
			this.uriComponentsBuilder.port(port);
			return this;
		}

		@Override
		public DefaultUriBuilder port(@Nullable String port) {
			this.uriComponentsBuilder.port(port);
			return this;
		}

		@Override
		public DefaultUriBuilder path(String path) {
			this.uriComponentsBuilder.path(path);
			return this;
		}

		@Override
		public DefaultUriBuilder replacePath(@Nullable String path) {
			this.uriComponentsBuilder.replacePath(path);
			return this;
		}

		@Override
		public DefaultUriBuilder pathSegment(String... pathSegments) {
			this.uriComponentsBuilder.pathSegment(pathSegments);
			return this;
		}

		@Override
		public DefaultUriBuilder query(String query) {
			this.uriComponentsBuilder.query(query);
			return this;
		}

		@Override
		public DefaultUriBuilder replaceQuery(@Nullable String query) {
			this.uriComponentsBuilder.replaceQuery(query);
			return this;
		}

		@Override
		public DefaultUriBuilder queryParam(String name, Object... values) {
			this.uriComponentsBuilder.queryParam(name, values);
			return this;
		}

		@Override
		public DefaultUriBuilder queryParam(String name, @Nullable Collection<?> values) {
			this.uriComponentsBuilder.queryParam(name, values);
			return this;
		}

		@Override
		public DefaultUriBuilder queryParamIfPresent(String name, Optional<?> value) {
			this.uriComponentsBuilder.queryParamIfPresent(name, value);
			return this;
		}

		@Override
		public DefaultUriBuilder queryParams(MultiValueMap<String, String> params) {
			this.uriComponentsBuilder.queryParams(params);
			return this;
		}

		@Override
		public DefaultUriBuilder replaceQueryParam(String name, Object... values) {
			this.uriComponentsBuilder.replaceQueryParam(name, values);
			return this;
		}

		@Override
		public DefaultUriBuilder replaceQueryParam(String name, @Nullable Collection<?> values) {
			this.uriComponentsBuilder.replaceQueryParam(name, values);
			return this;
		}

		@Override
		public DefaultUriBuilder replaceQueryParams(MultiValueMap<String, String> params) {
			this.uriComponentsBuilder.replaceQueryParams(params);
			return this;
		}

		@Override
		public DefaultUriBuilder fragment(@Nullable String fragment) {
			this.uriComponentsBuilder.fragment(fragment);
			return this;
		}

		@Override
		public URI build(Map<String, ?> uriVars) {
			if (!defaultUriVariables.isEmpty()) {
				Map<String, Object> map = new HashMap<>();
				map.putAll(defaultUriVariables);
				map.putAll(uriVars);
				uriVars = map;
			}
			if (encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY)) {
				uriVars = UriUtils.encodeUriVariables(uriVars);
			}

			this.uriComponentsBuilder.scheme(getScheme());
			this.uriComponentsBuilder.port(getPort());
			this.uriComponentsBuilder.replacePath(getContextPath());
			UriComponents uric = this.uriComponentsBuilder.build().expand(uriVars);
			return createUri(uric);
		}

		@Override
		public URI build(Object... uriVars) {
			if (ObjectUtils.isEmpty(uriVars) && !defaultUriVariables.isEmpty()) {
				return build(Collections.emptyMap());
			}
			if (encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY)) {
				uriVars = UriUtils.encodeUriVariables(uriVars);
			}
			UriComponents uric = this.uriComponentsBuilder.build().expand(uriVars);
			return createUri(uric);
		}

		private URI createUri(UriComponents uric) {
			if (encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT)) {
				uric = uric.encode();
			}
			return URI.create(uric.toString());
		}

		@Override
		public String toUriString() {
			return this.uriComponentsBuilder.build().toUriString();
		}

	}

}
