/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.mvc.config;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.http.HttpHeaders;

/**
 * Configuration properties for the {@link ProxyExchange} argument handler in
 * <code>@RequestMapping</code> methods.
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 * @author Joris Kuipers
 *
 */
@ConfigurationProperties("spring.cloud.gateway.proxy")
public class ProxyProperties {

	/**
	 * Contains headers that are considered case-sensitive by default.
	 */
	public static Set<String> DEFAULT_SENSITIVE = Set.of("cookie", "authorization");

	/**
	 * Contains headers that are skipped by default.
	 */
	public static Set<String> DEFAULT_SKIPPED = Set.of("content-length", "host");

	/**
	 * Fixed header values that will be added to all downstream requests.
	 */
	private Map<String, String> headers = new LinkedHashMap<>();

	/**
	 * A set of header names that should be sent downstream by default.
	 */
	private Set<String> autoForward = new HashSet<>();

	/**
	 * A set of sensitive header names that will not be sent downstream by default.
	 */
	private Set<String> sensitive = DEFAULT_SENSITIVE;

	/**
	 * A set of header names that will not be sent downstream because they could be
	 * problematic.
	 */
	private Set<String> skipped = DEFAULT_SKIPPED;

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public Set<String> getAutoForward() {
		return autoForward;
	}

	public void setAutoForward(Set<String> autoForward) {
		this.autoForward = autoForward;
	}

	public Set<String> getSensitive() {
		return sensitive;
	}

	public void setSensitive(Set<String> sensitive) {
		this.sensitive = sensitive;
	}

	public Set<String> getSkipped() {
		return skipped;
	}

	public void setSkipped(Set<String> skipped) {
		this.skipped = skipped;
	}

	public HttpHeaders convertHeaders() {
		HttpHeaders headers = new HttpHeaders();
		for (String key : this.headers.keySet()) {
			headers.set(key, this.headers.get(key));
		}
		return headers;
	}

}
