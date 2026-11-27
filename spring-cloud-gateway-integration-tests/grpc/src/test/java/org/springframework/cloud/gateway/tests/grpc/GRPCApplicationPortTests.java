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

package org.springframework.cloud.gateway.tests.grpc;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author MoChiUaena
 */
class GRPCApplicationPortTests {

	@Test
	void startsWhenAdjacentPortIsOccupiedAndReleasesGrpcPortOnClose() throws IOException {
		try (ServerSocket occupiedPort = occupyAdjacentPort()) {
			int grpcPort;
			try (ConfigurableApplicationContext context = SpringApplication.run(GRPCApplication.class,
					"--server.port=" + (occupiedPort.getLocalPort() - 1))) {
				grpcPort = context.getBean(GRPCApplication.GRPCServer.class).getPort();
				assertThat(grpcPort).isNotEqualTo(occupiedPort.getLocalPort());
			}
			try (ServerSocket rebound = new ServerSocket(grpcPort)) {
				assertThat(rebound.isBound()).isTrue();
			}
		}
	}

	private ServerSocket occupyAdjacentPort() throws IOException {
		for (int attempt = 0; attempt < 100; attempt++) {
			ServerSocket occupiedPort = new ServerSocket(0);
			try (ServerSocket availableHttpPort = new ServerSocket(occupiedPort.getLocalPort() - 1)) {
				return occupiedPort;
			}
			catch (IOException ex) {
				occupiedPort.close();
			}
		}
		throw new IOException("Could not find two adjacent ports for the test");
	}

}
