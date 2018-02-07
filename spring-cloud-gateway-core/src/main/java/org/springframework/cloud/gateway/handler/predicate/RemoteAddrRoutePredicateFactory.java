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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;

/**
 * @author Spencer Gibb
 */
public class RemoteAddrRoutePredicateFactory implements RoutePredicateFactory {

	private static final Log log = LogFactory.getLog(RemoteAddrRoutePredicateFactory.class);

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		validateMin(1, args);

		List<IpSubnetFilterRule> sources = new ArrayList<>();
		if (args != null) {
			for (Object arg : args.getValues()) {
				addSource(sources, (String) arg);
			}
		}
		return apply(sources);
	}

	public Predicate<ServerWebExchange> apply(String... addrs) {
		Assert.notEmpty(addrs, "addrs must not be empty");

		List<IpSubnetFilterRule> sources = new ArrayList<>();
		for (String addr : addrs) {
			addSource(sources, addr);
		}
		return apply(sources);
	}

	public Predicate<ServerWebExchange> apply(List<IpSubnetFilterRule> sources) {
		return exchange -> {
			InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
			if (remoteAddress != null) {
				String hostAddress = remoteAddress.getAddress().getHostAddress();
				String host = exchange.getRequest().getURI().getHost();

				if (!hostAddress.equals(host)) {
					log.warn("Remote addresses didn't match " + hostAddress + " != " + host);
				}

				for (IpSubnetFilterRule source : sources) {
					if (source.matches(remoteAddress)) {
						return true;
					}
				}
			}

			return false;
		};
	}

	private void addSource(List<IpSubnetFilterRule> sources, String source) {
		if (!source.contains("/")) { // no netmask, add default
			source = source + "/32";
		}

		String[] ipAddressCidrPrefix = source.split("/",2);
		String ipAddress = ipAddressCidrPrefix[0];
		int cidrPrefix = Integer.parseInt(ipAddressCidrPrefix[1]);

		sources.add(new IpSubnetFilterRule(ipAddress, cidrPrefix, IpFilterRuleType.ACCEPT));
	}
}
