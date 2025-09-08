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

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Spring Framework API Version strategy.
 */
@ConfigurationProperties(GatewayProperties.PREFIX + ".version")
@Validated
public class VersionProperties {

	/** The defaultVersion. */
	private String defaultVersion;

	/**
	 * Flag whether to use API versions that appear in mappings for supported version
	 * validation (true), or use only explicitly configured versions (false). Defaults to
	 * true.
	 */
	private boolean detectSupportedVersions = true;

	/** The header name used to extract the API Version. */
	private String headerName;

	/** The media type name used to extract the API Version. */
	private MediaType mediaType;

	/** The media type parameter name used to extract the API Version. */
	private String mediaTypeParamName;

	/** The index of a path segment used to extract the API Version. */
	private Integer pathSegment;

	/** The request parameter name used to extract the API Version. */
	private String requestParamName;

	private boolean required;

	private List<String> supportedVersions = new ArrayList<>();

	public String getDefaultVersion() {
		return defaultVersion;
	}

	public void setDefaultVersion(String defaultVersion) {
		this.defaultVersion = defaultVersion;
	}

	public boolean isDetectSupportedVersions() {
		return detectSupportedVersions;
	}

	public void setDetectSupportedVersions(boolean detectSupportedVersions) {
		this.detectSupportedVersions = detectSupportedVersions;
	}

	public String getHeaderName() {
		return headerName;
	}

	public void setHeaderName(String headerName) {
		this.headerName = headerName;
	}

	public MediaType getMediaType() {
		return mediaType;
	}

	public void setMediaType(MediaType mediaType) {
		this.mediaType = mediaType;
	}

	public String getMediaTypeParamName() {
		return mediaTypeParamName;
	}

	public void setMediaTypeParamName(String mediaTypeParamName) {
		this.mediaTypeParamName = mediaTypeParamName;
	}

	public Integer getPathSegment() {
		return pathSegment;
	}

	public void setPathSegment(Integer pathSegment) {
		this.pathSegment = pathSegment;
	}

	public String getRequestParamName() {
		return requestParamName;
	}

	public void setRequestParamName(String requestParamName) {
		this.requestParamName = requestParamName;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public List<String> getSupportedVersions() {
		return supportedVersions;
	}

	public void setSupportedVersions(List<String> supportedVersions) {
		this.supportedVersions = supportedVersions;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("defaultVersion", defaultVersion)
				.append("detectSupportedVersions", detectSupportedVersions)
				.append("headerName", headerName)
				.append("mediaType", mediaType)
				.append("mediaTypeParamName", mediaTypeParamName)
				.append("pathSegment", pathSegment)
				.append("requestParamName", requestParamName)
				.append("required", required)
				.append("supportedVersions", supportedVersions)
				.toString();
		// @formatter:on

	}

}
