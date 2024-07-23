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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/*

This route predicate allows requests to be filtered based on the "X-Forwarded-For" HTTP header.

This can be used with reverse proxies such as load balancers or web application firewalls where
the request should only be allowed if it comes from a trusted list of IP addresses used by those
reverse proxies.

With this implementation, a separate "XForwardedRemoteAddr" route predicate is offered which can
be configured with a list of allowed IP addresses and which by default has "maxTrustedIndex" set to 1.
This value means we trust the last (right-most) value in the "X-Forwarded-For" header, which represents
the last reverse proxy that was used when calling the gateway. That IP address is then checked against
the list of allowed IP addresses and used to determine whether or not the request is allowed.

See https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#modifying-the-way-remote-addresses-are-resolved.

Note that this predicate implementation does not implement any core logic itself, it aggregates the
"RemoteAddrRoutePredicateFactory" and "XForwardedRemoteAddressResolver" classes into a single predicate
that can be enabled directly from application configuration without the need to specify anything in
custom code.

Example usage in application.yml which trusts two reverse proxies (one using an IPv6 range):

  ...
  - predicates:
    - XForwardedRemoteAddr="20.103.252.85", "2a01:111:2050::/44"

*/

/**
 * @author Jelle Druyts
 */
public class XForwardedRemoteAddrRoutePredicateFactory
		extends AbstractRoutePredicateFactory<XForwardedRemoteAddrRoutePredicateFactory.Config> {

	private static final Log log = LogFactory.getLog(XForwardedRemoteAddrRoutePredicateFactory.class);

	public XForwardedRemoteAddrRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("sources");
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		if (log.isDebugEnabled()) {
			log.debug("Applying XForwardedRemoteAddr route predicate with maxTrustedIndex of "
					+ config.getMaxTrustedIndex() + " for " + config.getSources().size() + " source(s)");
		}

		// Reuse the standard RemoteAddrRoutePredicateFactory but instead of using the
		// default RemoteAddressResolver to determine the client IP address, use an
		// XForwardedRemoteAddressResolver.
		RemoteAddrRoutePredicateFactory.Config wrappedConfig = new RemoteAddrRoutePredicateFactory.Config();
		wrappedConfig.setSources(config.getSources());
		wrappedConfig
			.setRemoteAddressResolver(XForwardedRemoteAddressResolver.maxTrustedIndex(config.getMaxTrustedIndex()));
		RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory = new RemoteAddrRoutePredicateFactory();
		Predicate<ServerWebExchange> wrappedPredicate = remoteAddrRoutePredicateFactory.apply(wrappedConfig);

		return exchange -> {
			Boolean isAllowed = wrappedPredicate.test(exchange);

			if (log.isDebugEnabled()) {
				ServerHttpRequest request = exchange.getRequest();
				log.debug("Request for \"" + request.getURI() + "\" from client \""
						+ request.getRemoteAddress().getAddress().getHostAddress() + "\" with \""
						+ XForwardedRemoteAddressResolver.X_FORWARDED_FOR + "\" header value of \""
						+ request.getHeaders().get(XForwardedRemoteAddressResolver.X_FORWARDED_FOR) + "\" is "
						+ (isAllowed ? "ALLOWED" : "NOT ALLOWED"));
			}

			return isAllowed;
		};
	}

	public static class Config {

		// Trust the last (right-most) value in the "X-Forwarded-For" header by default,
		// which represents the last reverse proxy that was used when calling the gateway.
		private int maxTrustedIndex = 1;

		private List<String> sources = new ArrayList<>();

		public int getMaxTrustedIndex() {
			return this.maxTrustedIndex;
		}

		public Config setMaxTrustedIndex(int maxTrustedIndex) {
			this.maxTrustedIndex = maxTrustedIndex;
			return this;
		}

		public List<String> getSources() {
			return this.sources;
		}

		public Config setSources(List<String> sources) {
			this.sources = sources;
			return this;
		}

		public Config setSources(String... sources) {
			this.sources = Arrays.asList(sources);
			return this;
		}

	}

}
