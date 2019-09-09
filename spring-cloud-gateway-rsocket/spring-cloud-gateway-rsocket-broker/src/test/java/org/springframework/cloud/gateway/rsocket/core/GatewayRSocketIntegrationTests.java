/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.core;

import java.time.Duration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.rsocket.RSocketProperties;
import org.springframework.boot.rsocket.server.RSocketServerBootstrap;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.rsocket.test.PingPongApp;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = PingPongApp.class,
		properties = { "ping.take=10", "ping.subscribe=false" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
public class GatewayRSocketIntegrationTests {

	private static int port;

	@Autowired
	private PingPongApp.Ping ping;

	@Autowired
	private PingPongApp.Pong pong;

	@Autowired
	private RSocketProperties properties;

	@Autowired
	private PingPongApp.MySocketAcceptorFilter mySocketAcceptorFilter;

	@Autowired
	private RSocketServerBootstrap server;

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
	public void contextLoads() {
		// @formatter:off
		StepVerifier.create(ping.getPongFlux())
				.expectSubscription()
				.then(() -> server.stop())
				.thenConsumeWhile(s -> true)
				.expectComplete()
				.verify(Duration.ofSeconds(20));
		// @formatter:on

		assertThat(ping.getPongsReceived()).isGreaterThan(0);
		assertThat(pong.getPingsReceived()).isGreaterThan(0);
		Object server = properties.getServer();
		Object port = ReflectionTestUtils.invokeGetterMethod(server, "port");
		assertThat(port).isNotEqualTo(7002);
		assertThat(mySocketAcceptorFilter.invoked()).isTrue();
		assertThat(this.server.isRunning()).isFalse();
	}

}
