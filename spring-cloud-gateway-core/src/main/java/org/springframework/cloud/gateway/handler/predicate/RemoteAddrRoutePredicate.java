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
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.http.server.reactive.ReactorServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class RemoteAddrRoutePredicate implements RoutePredicate {

	private static final Log log = LogFactory.getLog(RemoteAddrRoutePredicate.class);

	@Override
	public Predicate<ServerWebExchange> apply(String... args) {
		validate(1, args);

		List<SubnetUtils> sources = new ArrayList<>();
		if (args != null) {
			for (String arg : args) {
				addSource(sources, arg);
			}
		}

		return exchange -> {
			ReactorServerHttpRequest request = (ReactorServerHttpRequest) exchange.getRequest();
			InetSocketAddress remoteAddress = request.getReactorRequest().remoteAddress();
			String hostAddress = remoteAddress.getAddress().getHostAddress();
			String host = exchange.getRequest().getURI().getHost();

			if (!hostAddress.equals(host)) {
				log.warn("Remote addresses didn't match " + hostAddress + " != " + host);
			}

			for (SubnetUtils source : sources) {
				if (source.getInfo().isInRange(hostAddress)) {
					return true;
				}
			}

			return false;
		};
	}

	private void addSource(List<SubnetUtils> sources, String source) {
		boolean inclusiveHostCount = false;
		if (!source.contains("/")) { // no netmask, add default
			source = source + "/32";
		}
		if (source.endsWith("/32")) {
			//http://stackoverflow.com/questions/2942299/converting-cidr-address-to-subnet-mask-and-network-address#answer-6858429
			inclusiveHostCount = true;
		}
		//TODO: howto support ipv6 as well?
		//TODO: copy SubnetUtils to remove commons-net dependency
		SubnetUtils subnetUtils = new SubnetUtils(source);
		subnetUtils.setInclusiveHostCount(inclusiveHostCount);
		sources.add(subnetUtils);
	}
}
