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

package org.springframework.cloud.gateway.server.mvc.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.MediaType;

@ConfigurationProperties(GatewayMvcProperties.PREFIX)
public class GatewayMvcProperties {

	/**
	 * Properties prefix.
	 */
	public static final String PREFIX = "spring.cloud.gateway.mvc";

	/**
	 * List of Routes.
	 */
	@NotNull
	@Valid
	private List<RouteProperties> routes = new ArrayList<>();

	/**
	 * Map of Routes.
	 */
	@NotNull
	@Valid
	private LinkedHashMap<String, RouteProperties> routesMap = new LinkedHashMap<>();

	private HttpClient httpClient = new HttpClient();

	/**
	 * Mime-types that are streaming.
	 */
	private List<MediaType> streamingMediaTypes = Arrays.asList(MediaType.TEXT_EVENT_STREAM,
			new MediaType("application", "stream+json"), new MediaType("application", "grpc"),
			new MediaType("application", "grpc+protobuf"), new MediaType("application", "grpc+json"));

	/**
	 * Buffer size for streaming media mime-types.
	 */
	private int streamingBufferSize = 16384;

	public List<RouteProperties> getRoutes() {
		return routes;
	}

	public void setRoutes(List<RouteProperties> routes) {
		this.routes = routes;
	}

	public LinkedHashMap<String, RouteProperties> getRoutesMap() {
		return routesMap;
	}

	public void setRoutesMap(LinkedHashMap<String, RouteProperties> routesMap) {
		this.routesMap = routesMap;
	}

	public HttpClient getHttpClient() {
		return httpClient;
	}

	public List<MediaType> getStreamingMediaTypes() {
		return streamingMediaTypes;
	}

	public void setStreamingMediaTypes(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	public int getStreamingBufferSize() {
		return streamingBufferSize;
	}

	public void setStreamingBufferSize(int streamingBufferSize) {
		this.streamingBufferSize = streamingBufferSize;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("httpClient", httpClient)
			.append("routes", routes)
			.append("routesMap", routesMap)
			.append("streamingMediaTypes", streamingMediaTypes)
			.append("streamingBufferSize", streamingBufferSize)
			.toString();
	}

	/**
	 * @deprecated in favor of spring.http.client.
	 */
	@Deprecated
	public static class HttpClient {

		/** The HttpClient connect timeout. */
		private Duration connectTimeout;

		/** The HttpClient read timeout. */
		private Duration readTimeout;

		/** The name of the SSL bundle to use. */
		private String sslBundle;

		/** The HttpClient type. Defaults to JDK. */
		private HttpClientType type = HttpClientType.JDK;

		@Deprecated
		@DeprecatedConfigurationProperty(replacement = "spring.http.client.connect-timeout", since = "4.2.0")
		public Duration getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		@Deprecated
		@DeprecatedConfigurationProperty(replacement = "spring.http.client.read-timeout", since = "4.2.0")
		public Duration getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}

		@Deprecated
		@DeprecatedConfigurationProperty(replacement = "spring.http.client.ssl.bundle", since = "4.2.0")
		public String getSslBundle() {
			return sslBundle;
		}

		public void setSslBundle(String sslBundle) {
			this.sslBundle = sslBundle;
		}

		@Deprecated
		public HttpClientType getType() {
			return type;
		}

		public void setType(HttpClientType type) {
			this.type = type;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("connectTimeout", connectTimeout)
				.append("readTimeout", readTimeout)
				.append("sslBundle", sslBundle)
				.append("type", type)
				.toString();
		}

	}

	public enum HttpClientType {

		/**
		 * Use JDK HttpClient.
		 */
		JDK,

		/**
		 * Auto-detect the HttpClient.
		 */
		AUTODETECT

	}

}
