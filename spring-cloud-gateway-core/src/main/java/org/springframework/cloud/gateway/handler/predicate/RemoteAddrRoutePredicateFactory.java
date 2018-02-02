/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.support.CIDRUtils;
import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class RemoteAddrRoutePredicateFactory implements RoutePredicateFactory {

	private static final Log log = LogFactory.getLog(RemoteAddrRoutePredicateFactory.class);

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		validateMin(1, args);

		List<CIDRUtils> sources = new ArrayList<>();
		if (args != null) {
			for (Object arg : args.getValues()) {
				addSource(sources, (String) arg);
			}
		}
		return apply(sources);
	}

	public Predicate<ServerWebExchange> apply(String... addrs) {
		Assert.notEmpty(addrs, "addrs must not be empty");

		List<CIDRUtils> sources = new ArrayList<>();
		for (String addr : addrs) {
			addSource(sources, addr);
		}
		return apply(sources);
	}

	public Predicate<ServerWebExchange> apply(List<CIDRUtils> sources) {
		return exchange -> {
			InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
			if (remoteAddress != null) {
				String hostAddress = remoteAddress.getAddress().getHostAddress();
				String host = exchange.getRequest().getURI().getHost();

				if (!hostAddress.equals(host)) {
					log.warn("Remote addresses didn't match " + hostAddress + " != " + host);
				}

				Optional<InetAddress> inetAddress = getInetAddress(hostAddress);
				if(inetAddress.isPresent()) {
					for (CIDRUtils source : sources) {
						if (source.isInRange(inetAddress.get())) {
							return true;
						}
					}
				}

			}

			return false;
		};
	}

	private void addSource(List<CIDRUtils> sources, String source) {
		if (!source.contains("/")) { // no netmask, add default
			source = source + "/32";
		}

		try {
			sources.add(new CIDRUtils(source));
		} catch (UnknownHostException e) {
			log.warn(String.format("Remote address %s is an unknown host", source));
		}
	}

	private Optional<InetAddress> getInetAddress(String hostAddress) {
		try {
			InetAddress address = InetAddress.getByName(hostAddress);
			return Optional.of(address);
		} catch (UnknownHostException e) {
			log.error(String.format("Host address %s is an unknown host", hostAddress), e);
		}

		return Optional.empty();
	}

}
