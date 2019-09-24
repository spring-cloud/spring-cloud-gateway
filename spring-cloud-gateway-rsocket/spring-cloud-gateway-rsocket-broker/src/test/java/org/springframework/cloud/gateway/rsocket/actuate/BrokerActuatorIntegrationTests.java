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

import java.math.BigInteger;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.rsocket.cluster.ClusterService;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.rsocket.actuate.BrokerActuator.BROKER_INFO_PATH;
import static org.springframework.cloud.gateway.rsocket.actuate.BrokerActuator.ROUTE_JOIN_PATH;
import static org.springframework.cloud.gateway.rsocket.actuate.BrokerActuator.ROUTE_REMOVE_PATH;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.rsocket.cluster.enabled=false",
				"spring.cloud.gateway.rsocket.broker.actuator.enabled=true" })
public class BrokerActuatorIntegrationTests {

	private final Random random = new Random();

	@Autowired
	private RSocketRequester.Builder requesterBuilder;

	@Autowired
	private RSocketMessageHandler messageHandler;

	@MockBean
	private ClusterService clusterService;

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

		Mono<BigInteger> result = callActuator(brokerId, BigInteger.class, data,
				BROKER_INFO_PATH);

		StepVerifier.create(result).consumeNextWith(
				res -> assertThat(res).isNotNull().isEqualTo(BigInteger.valueOf(1234L)))
				.verifyComplete();

		// TODO: assert server side calls worked
	}

	@Test
	@Ignore // TODO: move to integration tests module
	public void routeJoinRemoveWorks() {
		long brokerId = random.nextLong();
		long routeId = random.nextLong();

		RouteJoin data = RouteJoin.builder().brokerId(brokerId).routeId(routeId)
				.serviceName("testServiceName").build();

		RSocketRequester requester = getRequester(brokerId);
		Mono<RouteJoin> result = callActuator(requester, brokerId, RouteJoin.class, data,
				ROUTE_JOIN_PATH);

		StepVerifier.create(result)
				.consumeNextWith(res -> assertThat(res).isNotNull().isEqualTo(data))
				.verifyComplete();
		// TODO: assert server side calls worked

		routeRemoveWorks(requester, routeId);
	}

	public void routeRemoveWorks(RSocketRequester requester, long routeId) {
		long brokerId = random.nextLong();
		RouteRemove data = RouteRemove.builder().brokerId(brokerId).routeId(routeId)
				.build();

		Mono<Boolean> result = callActuator(requester, brokerId, Boolean.class, data,
				ROUTE_REMOVE_PATH);

		StepVerifier.create(result).consumeNextWith(res -> assertThat(res).isTrue())
				.verifyComplete();
		// TODO: assert server side calls worked

		result = callActuator(brokerId, Boolean.class, data, ROUTE_REMOVE_PATH);

		StepVerifier.create(result).consumeNextWith(res -> assertThat(res).isTrue())
				.verifyComplete();
	}

	@Test
	@Ignore // TODO: move to integration tests module
	public void routeJoinCloseDeregisters() {
		long brokerId = random.nextLong();
		long routeId = random.nextLong();

		RouteJoin data = RouteJoin.builder().brokerId(brokerId).routeId(routeId)
				.serviceName("testServiceName").build();

		RSocketRequester requester = getRequester(brokerId);
		Mono<RouteJoin> result = callActuator(requester, brokerId, RouteJoin.class, data,
				ROUTE_JOIN_PATH);

		result.block();
		StepVerifier.create(result)
				.consumeNextWith(res -> assertThat(res).isNotNull().isEqualTo(data))
				.verifyComplete();

		requester.rsocket().dispose();

		// TODO: assert server side calls worked
	}

	private <T, D> Mono<T> callActuator(long brokerId, Class<T> type, D data,
			String path) {
		RSocketRequester requester = getRequester(brokerId);

		return callActuator(requester, brokerId, type, data, path);
	}

	private <T, D> Mono<T> callActuator(RSocketRequester requester, long brokerId,
			Class<T> type, D data, String path) {

		Forwarding forwarding = Forwarding.of(brokerId).serviceName("gateway")
				.disableProxy().build();

		return requester.route(path).metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE)
				.data(data).retrieveMono(type);
	}

	private RSocketRequester getRequester(long brokerId) {
		RouteSetup routeSetup = RouteSetup.of(brokerId, "gateway")
				.with("proxy", Boolean.FALSE.toString()).build();
		// mimic rsocket client autoconfig
		return requesterBuilder
				.setupMetadata(routeSetup, RouteSetup.ROUTE_SETUP_MIME_TYPE)
				.rsocketFactory(rsocketFactory -> rsocketFactory
						.acceptor(messageHandler.responder()))
				.connectTcp("localhost", port).block();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Config {

	}

}
