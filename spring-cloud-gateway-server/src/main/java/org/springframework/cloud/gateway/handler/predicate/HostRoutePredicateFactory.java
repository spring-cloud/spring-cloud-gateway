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

package org.springframework.cloud.gateway.handler.predicate;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HostRoutePredicateFactory extends AbstractRoutePredicateFactory<HostRoutePredicateFactory.Config> {

	private boolean includePort = true;

	private PathMatcher pathMatcher = new AntPathMatcher(".");

	public HostRoutePredicateFactory() {
		this(true);
	}

	public HostRoutePredicateFactory(boolean includePort) {
		super(Config.class);
		this.includePort = includePort;
	}

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	/* for testing */ void setIncludePort(boolean includePort) {
		this.includePort = includePort;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("patterns");
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				String host;
				if (includePort) {
					host = exchange.getRequest().getHeaders().getFirst("Host");
				}
				else {
					InetSocketAddress address = exchange.getRequest().getHeaders().getHost();
					if (address != null) {
						host = address.getHostString();
					}
					else {
						return false;
					}
				}

				String match = null;
				for (int i = 0; i < config.getPatterns().size(); i++) {
					String pattern = config.getPatterns().get(i);
					if (pathMatcher.match(pattern, host)) {
						match = pattern;
						break;
					}
				}

				if (match != null) {
					Map<String, String> variables = pathMatcher.extractUriTemplateVariables(match, host);
					ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
					return true;
				}

				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Hosts: %s", config.getPatterns());
			}
		};
	}

	public static class Config {

		private List<String> patterns = new ArrayList<>();

		public List<String> getPatterns() {
			return patterns;
		}

		public Config setPatterns(List<String> patterns) {
			this.patterns = patterns;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("patterns", patterns).toString();
		}

	}

}
