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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotBlank;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.core.style.ToStringCreator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.accept.ApiVersionHolder;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.accept.ApiVersionStrategy;
import org.springframework.web.reactive.accept.DefaultApiVersionStrategy;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class VersionRoutePredicateFactory extends AbstractRoutePredicateFactory<VersionRoutePredicateFactory.Config> {

	private static final Log log = LogFactory.getLog(VersionRoutePredicateFactory.class);

	private final @Nullable ApiVersionStrategy apiVersionStrategy;

	public VersionRoutePredicateFactory(@Nullable ApiVersionStrategy apiVersionStrategy) {
		super(Config.class);
		this.apiVersionStrategy = apiVersionStrategy;
	}

	private static void traceMatch(String prefix, @Nullable Object desired, @Nullable Object actual, boolean match) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("%s \"%s\" %s against value \"%s\"", prefix, desired,
					match ? "matches" : "does not match", actual));
		}
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("version");
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {

		if (apiVersionStrategy instanceof DefaultApiVersionStrategy strategy) {
			String version = config.version;
			if (version != null) {
				strategy
					.addMappedVersion((version.endsWith("+") ? version.substring(0, version.length() - 1) : version));
			}
		}

		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				ServerHttpRequest request = exchange.getRequest();
				if (config.parsedVersion == null) {
					Assert.state(apiVersionStrategy != null, "No ApiVersionStrategy to parse version with");
					String version = config.version;
					Objects.requireNonNull(version, "version must not be null");
					config.parsedVersion = apiVersionStrategy.parseVersion(version);
				}

				ApiVersionHolder requestVersion = (ApiVersionHolder) request.getAttributes()
					.get(HandlerMapping.API_VERSION_ATTRIBUTE);

				if (requestVersion == null || !requestVersion.hasVersion()) {
					traceMatch("Version", config.version, null, false);
					return true;
				}

				int result = compareVersions(config.parsedVersion, requestVersion.getVersion());
				boolean match = (config.baselineVersion ? result <= 0 : result == 0);
				traceMatch("Version", config.version, requestVersion, match);
				return match;
			}

			private <V extends Comparable<V>> int compareVersions(Object v1, Object v2) {
				return ((V) v1).compareTo((V) v2);
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Version: %s", config.version + (config.baselineVersion ? "+" : ""));
			}
		};
	}

	public static class Config {

		private boolean baselineVersion;

		@NotBlank
		private @Nullable String version;

		private @Nullable String originalVersion;

		private @Nullable Comparable<?> parsedVersion;

		public boolean isBaselineVersion() {
			return baselineVersion;
		}

		public @Nullable String getOriginalVersion() {
			return originalVersion;
		}

		public @Nullable Comparable<?> getParsedVersion() {
			return parsedVersion;
		}

		public @Nullable String getVersion() {
			return version;
		}

		public Config setVersion(Object version) {
			if (version instanceof String s) {
				this.originalVersion = s;
				this.baselineVersion = s.endsWith("+");
				this.version = initVersion(s, this.baselineVersion);
			}
			else {
				this.baselineVersion = false;
				this.version = version.toString();
				this.originalVersion = version.toString();
				this.parsedVersion = (Comparable<?>) version;
			}
			return this;
		}

		private static String initVersion(String version, boolean baselineVersion) {
			return (baselineVersion ? version.substring(0, version.length() - 1) : version);
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("baselineVersion", baselineVersion)
				.append("version", version)
				.toString();
		}

	}

}
