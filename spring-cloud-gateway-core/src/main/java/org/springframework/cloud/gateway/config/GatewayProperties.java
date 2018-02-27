/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway")
@Validated
public class GatewayProperties {

	/**
	 * List of Routes
	 */
	@NotNull
	@Valid
	private List<RouteDefinition> routes = new ArrayList<>();

	/**
	 * List of filter definitions that are applied to every route.
	 */
	private List<FilterDefinition> defaultFilters = new ArrayList<>();

	private List<MediaType> streamingMediaTypes = Arrays.asList(MediaType.TEXT_EVENT_STREAM,
			MediaType.APPLICATION_STREAM_JSON);

	public List<RouteDefinition> getRoutes() {
		return routes;
	}

	public void setRoutes(List<RouteDefinition> routes) {
		this.routes = routes;
	}

	public List<FilterDefinition> getDefaultFilters() {
		return defaultFilters;
	}

	public void setDefaultFilters(List<FilterDefinition> defaultFilters) {
		this.defaultFilters = defaultFilters;
	}

	public List<MediaType> getStreamingMediaTypes() {
		return streamingMediaTypes;
	}

	public void setStreamingMediaTypes(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	@Override
	public String toString() {
		return "GatewayProperties{" +
				"routes=" + routes +
				", defaultFilters=" + defaultFilters +
				", streamingMediaTypes=" + streamingMediaTypes +
				'}';
	}
}
