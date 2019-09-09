/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.actuate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GatewayRSocketActuator {

	private static final Log log = LogFactory.getLog(GatewayRSocketActuator.class);

	/**
	 * Path for BrokerInfo actuator endpoint.
	 */
	public static final String BROKER_INFO_PATH = "/actuator/gateway/brokerinfo";

	/**
	 * Path for RouteJoin actuator endpoint.
	 */
	public static final String ROUTE_JOIN_PATH = "/actuator/gateway/routejoin";

	/**
	 * Path for RouteJoin actuator endpoint.
	 */
	public static final String ROUTE_REMOVE_PATH = "/actuator/gateway/routeremove";

	@MessageMapping("hello")
	public Mono<String> hello(String name) {
		return Mono.just("Hello " + name);
	}

	@MessageMapping(BROKER_INFO_PATH)
	public BrokerInfo brokerInfo(BrokerInfo brokerInfo) {
		log.info("BrokerInfo: " + brokerInfo);
		return brokerInfo;
	}

	@MessageMapping(ROUTE_JOIN_PATH)
	public RouteJoin routeJoin(RouteJoin routeJoin) {
		log.info("RouteJoin: " + routeJoin);
		return routeJoin;
	}

	@MessageMapping(ROUTE_REMOVE_PATH)
	public RouteRemove routeRemove(RouteRemove routeRemove) {
		log.info("RouteRemove: " + routeRemove);
		return routeRemove;
	}

}
