/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.support;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.reactive.accept.ApiVersionDeprecationHandler;
import org.springframework.web.reactive.accept.ApiVersionResolver;
import org.springframework.web.reactive.accept.DefaultApiVersionStrategy;
import org.springframework.web.server.ServerWebExchange;

/**
 * A custom ApiVersionStrategry that ignores version validation. The gateway should return
 * a 404 rather than a 400.
 */
public class GatewayApiVersionStrategy extends DefaultApiVersionStrategy {

	/**
	 * Create an instance.
	 * @param versionResolvers one or more resolvers to try; the first non-null value
	 * returned by any resolver becomes the resolved used
	 * @param versionParser parser for to raw version values
	 * @param versionRequired whether a version is required; if a request does not have a
	 * version, and a {@code defaultVersion} is not specified, validation fails with
	 * {@link MissingApiVersionException}
	 * @param defaultVersion a default version to assign to requests that don't specify
	 * one
	 * @param detectSupportedVersions whether to use API versions that appear in mappings
	 * for supported version validation (true), or use only explicitly configured versions
	 * (false).
	 * @param deprecationHandler handler to send hints and information about deprecated
	 * API versions to clients
	 */
	public GatewayApiVersionStrategy(List<ApiVersionResolver> versionResolvers, ApiVersionParser<?> versionParser,
			boolean versionRequired, @Nullable String defaultVersion, boolean detectSupportedVersions,
			@Nullable ApiVersionDeprecationHandler deprecationHandler) {
		super(versionResolvers, versionParser, versionRequired, defaultVersion, detectSupportedVersions,
				deprecationHandler);
	}

	@Override
	public @Nullable Comparable<?> resolveParseAndValidateVersion(ServerWebExchange exchange) {
		try {
			return super.resolveParseAndValidateVersion(exchange);
		}
		catch (InvalidApiVersionException e) {
			// ignore, so gateway will 404, not 400
			return null;
		}
	}

	@Override
	public void validateVersion(@Nullable Comparable<?> requestVersion, ServerWebExchange exchange)
			throws MissingApiVersionException, InvalidApiVersionException {
		try {
			super.validateVersion(requestVersion, exchange);
		}
		catch (InvalidApiVersionException e) {
			// ignore, so gateway will 404, not 400
		}
	}

}
