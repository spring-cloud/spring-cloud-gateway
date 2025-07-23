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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

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
public class RewriteLocationResponseHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewriteLocationResponseHeaderGatewayFilterFactory.Config> {

	private static final String STRIP_VERSION_KEY = "stripVersion";

	private static final String LOCATION_HEADER_NAME_KEY = "locationHeaderName";

	private static final String HOST_VALUE_KEY = "hostValue";

	private static final String PROTOCOLS_KEY = "protocols";

	private static final Pattern VERSIONED_PATH = Pattern.compile("^/v\\d+/.*");

	private static final String DEFAULT_PROTOCOLS = "https?|ftps?";

	private static final Pattern DEFAULT_HOST_PORT = compileHostPortPattern(DEFAULT_PROTOCOLS);

	private static final Pattern DEFAULT_HOST_PORT_VERSION = compileHostPortVersionPattern(DEFAULT_PROTOCOLS);

	public RewriteLocationResponseHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	private static Pattern compileHostPortPattern(String protocols) {
		return Pattern.compile("(?<=^(?:" + protocols + ")://)[^:/]+(?::\\d+)?(?=/)");
	}

	private static Pattern compileHostPortVersionPattern(String protocols) {
		return Pattern.compile("(?<=^(?:" + protocols + ")://)[^:/]+(?::\\d+)?(?:/v\\d+)?(?=/)");
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STRIP_VERSION_KEY, LOCATION_HEADER_NAME_KEY, HOST_VALUE_KEY, PROTOCOLS_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange).then(Mono.fromRunnable(() -> rewriteLocation(exchange, config)));
			}

			@Override
			public String toString() {
				// @formatter:off
				return filterToStringCreator(
						RewriteLocationResponseHeaderGatewayFilterFactory.this)
						.append("stripVersion", config.stripVersion)
						.append("locationHeaderName", config.locationHeaderName)
						.append("hostValue", config.hostValue)
						.append("protocols", config.protocols)
						.toString();
				// @formatter:on
			}
		};
	}

	void rewriteLocation(ServerWebExchange exchange, Config config) {
		final String location = exchange.getResponse().getHeaders().getFirst(config.getLocationHeaderName());
		final String host = config.getHostValue() != null ? config.getHostValue()
				: exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
		final String path = exchange.getRequest().getURI().getPath();
		if (location != null && host != null) {
			final String fixedLocation = fixedLocation(location, host, path, config.getStripVersion(),
					config.getHostPortPattern(), config.getHostPortVersionPattern());
			exchange.getResponse().getHeaders().set(config.getLocationHeaderName(), fixedLocation);
		}
	}

	String fixedLocation(String location, String host, String path, StripVersion stripVersion, Pattern hostPortPattern,
			Pattern hostPortVersionPattern) {
		final boolean doStrip = StripVersion.ALWAYS_STRIP.equals(stripVersion)
				|| (StripVersion.AS_IN_REQUEST.equals(stripVersion) && !VERSIONED_PATH.matcher(path).matches());
		final Pattern pattern = doStrip ? hostPortVersionPattern : hostPortPattern;
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

	public static class Config {

		private StripVersion stripVersion = StripVersion.AS_IN_REQUEST;

		private String locationHeaderName = HttpHeaders.LOCATION;

		private String hostValue;

		private String protocols = DEFAULT_PROTOCOLS;

		private Pattern hostPortPattern = DEFAULT_HOST_PORT;

		private Pattern hostPortVersionPattern = DEFAULT_HOST_PORT_VERSION;

		public StripVersion getStripVersion() {
			return stripVersion;
		}

		public Config setStripVersion(StripVersion stripVersion) {
			this.stripVersion = stripVersion;
			return this;
		}

		public String getLocationHeaderName() {
			return locationHeaderName;
		}

		public Config setLocationHeaderName(String locationHeaderName) {
			this.locationHeaderName = locationHeaderName;
			return this;
		}

		public String getHostValue() {
			return hostValue;
		}

		public Config setHostValue(String hostValue) {
			this.hostValue = hostValue;
			return this;
		}

		public String getProtocols() {
			return protocols;
		}

		public Config setProtocols(String protocols) {
			this.protocols = protocols;
			this.hostPortPattern = compileHostPortPattern(protocols);
			this.hostPortVersionPattern = compileHostPortVersionPattern(protocols);
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
