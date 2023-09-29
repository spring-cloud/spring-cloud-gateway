/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.gateway.tests.http2.nossl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Hooks;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.tests.http2.Http2Application;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.TestSocketUtils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import static org.springframework.cloud.gateway.tests.http2.Http2ApplicationTests.assertResponse;

/**
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = Http2Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class NosslTests {

	@LocalServerPort
	int port;

	@BeforeAll
	static void beforeAll() {
		int noSslPort = TestSocketUtils.findAvailableTcpPort();
		System.setProperty("nossl.port", String.valueOf(noSslPort));
	}

	@AfterAll
	static void afterAll() {
		System.clearProperty("nossl.port");
	}

	@Test
	public void http2TerminationWorks(CapturedOutput output) {
		int nosslPort = Integer.parseInt(System.getProperty("nossl.port"));
		System.err.println("nossl.port = " + nosslPort);
		Hooks.onOperatorDebug();
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NosslConfiguration.class)
				.properties("server.port=" + nosslPort).profiles("nossl").run()) {
			String uri = "https://localhost:" + port + "/nossl";
			String expected = "nossl";
			assertResponse(uri, expected);
			Assertions.assertThat(output).doesNotContain("PRI * HTTP/2.0");
		}
	}

}
