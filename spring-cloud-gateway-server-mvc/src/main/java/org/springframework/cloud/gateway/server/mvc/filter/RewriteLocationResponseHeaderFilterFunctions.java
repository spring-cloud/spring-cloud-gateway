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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/*
This filter intent is to modify the value of Location response header, ridding it of backend specific details.
Typical scenario: POST response from a legacy backend contains a Location header with backend's hostname.
				  You want to replace the backend's hostname:port with your documented gateway's hostname:port.
Additionally, sometimes backend returns a 'versioned' Location, and you may want to strip the version portion.

Note that the protocol portion of the URL will not be replaced by this filter. If you need that too,
either follow this filter with a generic rewrite response header filter, or subclass this filter.

Configuration parameters:
- stripVersion
	NEVER_STRIP - Version will not be stripped, even if the original request path contains no version
	AS_IN_REQUEST - Default. Version will be stripped only if the original request path contains no version
	ALWAYS_STRIP - Version will be stripped, even if the original request path contains version
- locationHeaderName
	String representing a custom location header name. Default - Location
- hostValue
	A String. If provided, will be used to replace the host:port portion of the response Location header.
	Default - The value of request Host header is used to replace.
- protocols
	A valid regex String, against which the protocol name will be matched. If not matched, the filter will do nothing.
	Default - https?|ftps? (which is the same as http|https|ftp|ftps)

Example 1
	  default-filters:
	  - RewriteLocationResponseHeader

Host request header: api.example.com:443
POST request path: /some/object/name
Location response header: https://object-service.prod.example.net/v2/some/object/id
Modified Location response header: https://api.example.com:443/some/object/id

Example 2
	  default-filters:
	  - name: RewriteLocationResponseHeader
		args:
		  stripVersion: ALWAYS_STRIP
		  locationHeaderName: Link
		  hostValue: example-api.com

Host request header (irrelevant): api.example.com:443
POST request path: /v1/some/object/name
Link response header: https://object-service.prod.example.net/v1/some/object/id
Modified Link response header: https://example-api.com/some/object/id

Example 3
	  default-filters:
	  - name: RewriteLocationResponseHeader
		args:
		  stripVersion: NEVER_STRIP
		  protocols: https|ftps # only replace host:port for https or ftps, but not http or ftp

Host request header: api.example.com:443
1. POST request path: /some/object/name
Location response header: https://object-service.prod.example.net/v1/some/object/id
Modified Location response header: https://api.example.com/v1/some/object/id
2. POST request path: /some/other/object/name
Location response header: http://object-service.prod.example.net/v1/some/object/id
Modified (not) Location response header: http://object-service.prod.example.net/v1/some/object/id
 */

/**
 * @author Vitaliy Pavlyuk
 */
public abstract class RewriteLocationResponseHeaderFilterFunctions {

	private static final Pattern VERSIONED_PATH = Pattern.compile("^/v\\d+/.*");

	private static final String DEFAULT_PROTOCOLS = "https?|ftps?";

	private static final Pattern DEFAULT_HOST_PORT = compileHostPortPattern(DEFAULT_PROTOCOLS);

	private static final Pattern DEFAULT_HOST_PORT_VERSION = compileHostPortVersionPattern(DEFAULT_PROTOCOLS);

	private RewriteLocationResponseHeaderFilterFunctions() {
	}

	private static Pattern compileHostPortPattern(String protocols) {
		return Pattern.compile("(?<=^(?:" + protocols + ")://)[^:/]+(?::\\d+)?(?=/)");
	}

	private static Pattern compileHostPortVersionPattern(String protocols) {
		return Pattern.compile("(?<=^(?:" + protocols + ")://)[^:/]+(?::\\d+)?(?:/v\\d+)?(?=/)");
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> rewriteLocationResponseHeader(
			Consumer<RewriteLocationResponseHeaderConfig> configConsumer) {
		RewriteLocationResponseHeaderConfig config = new RewriteLocationResponseHeaderConfig();
		configConsumer.accept(config);
		return (request, response) -> {
			String location = response.headers().getFirst(config.getLocationHeaderName());
			String host = config.getHostValue() != null ? config.getHostValue()
					: request.headers().firstHeader(HttpHeaders.HOST);
			String path = request.uri().getPath();
			if (location != null && host != null) {
				String fixedLocation = fixedLocation(location, host, path, config.getStripVersion(),
						config.getHostPortPattern(), config.getHostPortVersionPattern());
				response.headers().set(config.getLocationHeaderName(), fixedLocation);
			}
			return response;
		};
	}

	private static String fixedLocation(String location, String host, String path, StripVersion stripVersion,
			Pattern hostPortPattern, Pattern hostPortVersionPattern) {
		boolean doStrip = StripVersion.ALWAYS_STRIP.equals(stripVersion)
				|| (StripVersion.AS_IN_REQUEST.equals(stripVersion) && !VERSIONED_PATH.matcher(path).matches());
		Pattern pattern = doStrip ? hostPortVersionPattern : hostPortPattern;
		return pattern.matcher(location).replaceFirst(host);
	}

	public enum StripVersion {

		/**
		 * Version will not be stripped, even if the original request path contains no
		 * version.
		 */
		NEVER_STRIP,

		/**
		 * Version will be stripped only if the original request path contains no version.
		 * This is the Default.
		 */
		AS_IN_REQUEST,

		/**
		 * Version will be stripped, even if the original request path contains version.
		 */
		ALWAYS_STRIP

	}

	public static class RewriteLocationResponseHeaderConfig {

		private StripVersion stripVersion = StripVersion.AS_IN_REQUEST;

		private String locationHeaderName = HttpHeaders.LOCATION;

		private String hostValue;

		private String protocolsRegex = DEFAULT_PROTOCOLS;

		private Pattern hostPortPattern = DEFAULT_HOST_PORT;

		private Pattern hostPortVersionPattern = DEFAULT_HOST_PORT_VERSION;

		public StripVersion getStripVersion() {
			return stripVersion;
		}

		public RewriteLocationResponseHeaderConfig setStripVersion(String stripVersion) {
			this.stripVersion = StripVersion.valueOf(stripVersion);
			return this;
		}

		public RewriteLocationResponseHeaderConfig setStripVersion(StripVersion stripVersion) {
			this.stripVersion = stripVersion;
			return this;
		}

		public String getLocationHeaderName() {
			return locationHeaderName;
		}

		public RewriteLocationResponseHeaderConfig setLocationHeaderName(String locationHeaderName) {
			this.locationHeaderName = locationHeaderName;
			return this;
		}

		public String getHostValue() {
			return hostValue;
		}

		public RewriteLocationResponseHeaderConfig setHostValue(String hostValue) {
			this.hostValue = hostValue;
			return this;
		}

		public String getProtocolsRegex() {
			return protocolsRegex;
		}

		public RewriteLocationResponseHeaderConfig setProtocolsRegex(String protocolsRegex) {
			this.protocolsRegex = protocolsRegex;
			this.hostPortPattern = compileHostPortPattern(protocolsRegex);
			this.hostPortVersionPattern = compileHostPortVersionPattern(protocolsRegex);
			return this;
		}

		public Pattern getHostPortPattern() {
			return hostPortPattern;
		}

		public Pattern getHostPortVersionPattern() {
			return hostPortVersionPattern;
		}

	}

}
