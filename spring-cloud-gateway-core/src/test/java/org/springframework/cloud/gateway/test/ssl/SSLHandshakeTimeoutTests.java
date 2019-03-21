/*
 * Copyright 2013-2017 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.test.ssl;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.JsonPathAssertions;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@RunWith(SpringRunner.class)
// this test works because it assumes TLS hand shake cannot be done in 1ms. It takes closer to 80ms
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"spring.cloud.gateway.httpclient.ssl.handshake-timeout-millis=1"})
@DirtiesContext
@ActiveProfiles("ssl")
public class SSLHandshakeTimeoutTests extends SingleCertSSLTests {

	@Test
	@Override // here we validate that it the handshake times out
	public void testSslTrust() {
		ResponseSpec responseSpec = testClient.get().uri("/ssltrust").exchange();
		responseSpec.expectStatus().is5xxServerError();
		JsonPathAssertions jsonPath = responseSpec.expectBody().jsonPath("message");
		jsonPath.isEqualTo("handshake timed out");
	}
	

}
