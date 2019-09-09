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

import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.rsocket.actuate.GatewayRSocketActuator.BROKER_INFO_PATH;
import static org.springframework.cloud.gateway.rsocket.actuate.GatewayRSocketActuator.ROUTE_JOIN_PATH;
import static org.springframework.cloud.gateway.rsocket.actuate.GatewayRSocketActuator.ROUTE_REMOVE_PATH;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class GatewayRSocketActuatorIntegrationTests {

	private final Random random = new Random();

	@Autowired
	private RSocketRequester.Builder requesterBuilder;

	// @LocalServerPort
	private static int port;

	@BeforeClass
	public static void init() {
		Hooks.onOperatorDebug();
		port = SocketUtils.findAvailableTcpPort();
		System.setProperty("spring.rsocket.server.port", String.valueOf(port));
	}

	@AfterClass
	public static void after() {
		System.clearProperty("spring.rsocket.server.port");
	}

	@Test
	public void brokerInfoWorks() {
		long brokerId = random.nextLong();

		BrokerInfo data = BrokerInfo.of(brokerId).build();

		Mono<BrokerInfo> result = callActuator(brokerId, BrokerInfo.class, data,
				BROKER_INFO_PATH);

		StepVerifier.create(result)
				.consumeNextWith(res -> assertThat(res).isNotNull().isEqualTo(data))
				.verifyComplete();
	}

	@Test
	public void routeJoinWorks() {
		long brokerId = random.nextLong();
		long routeId = random.nextLong();

		RouteJoin data = RouteJoin.builder().brokerId(brokerId).routeId(routeId)
				.serviceName("testServiceName").build();

		Mono<RouteJoin> result = callActuator(brokerId, RouteJoin.class, data,
				ROUTE_JOIN_PATH);

		StepVerifier.create(result)
				.consumeNextWith(res -> assertThat(res).isNotNull().isEqualTo(data))
				.verifyComplete();
	}

	@Test
	public void routeRemoveWorks() {
		long brokerId = random.nextLong();
		long routeId = random.nextLong();

		RouteRemove data = RouteRemove.builder().brokerId(brokerId).routeId(routeId)
				.build();

		Mono<RouteRemove> result = callActuator(brokerId, RouteRemove.class, data,
				ROUTE_REMOVE_PATH);

		StepVerifier.create(result)
				.consumeNextWith(res -> assertThat(res).isNotNull().isEqualTo(data))
				.verifyComplete();
	}

	private <T> Mono<T> callActuator(long brokerId, Class<T> type, T data, String path) {
		RouteSetup routeSetup = RouteSetup.of(brokerId, "brokerInfoTest").build();

		RSocketRequester requester = requesterBuilder
				.setupMetadata(routeSetup, RouteSetup.ROUTE_SETUP_MIME_TYPE)
				.connectTcp("localhost", port).block();

		Forwarding forwarding = Forwarding.of(brokerId).serviceName("gateway")
				.disableProxy().build();

		return requester.route(path).metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE)
				.data(data).retrieveMono(type);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Config {

	}

}
