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

package org.springframework.cloud.gateway.test.ssl;

import java.net.URL;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ResourceUtils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("client-auth-ssl")
public class ClientCertAuthSSLTests extends SingleCertSSLTests {

	@Value("${spring.cloud.gateway.httpclient.ssl.key-store}")
	private String keyStore;

	@Value("${spring.cloud.gateway.httpclient.ssl.key-store-password}")
	private String keyStorePassword;

	@Before
	public void setup() throws Exception {

		KeyStore store = null;

		store = KeyStore.getInstance("PKCS12");

		URL url = ResourceUtils.getURL(keyStore);
		store.load(url.openStream(), keyStorePassword.toCharArray());

		KeyManagerFactory keyManagerFactory = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());

		keyManagerFactory.init(store, keyStorePassword.toCharArray());

		try {
			SslContext sslContext = SslContextBuilder.forClient()
					.trustManager(InsecureTrustManagerFactory.INSTANCE)
					.keyManager(keyManagerFactory).build();
			HttpClient httpClient = HttpClient.create()
					.secure(ssl -> ssl.sslContext(sslContext));
			setup(new ReactorClientHttpConnector(httpClient),
					"https://localhost:" + port);
		}
		catch (SSLException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testSslTrust() {
		testClient.get().uri("/ssltrust").exchange().expectStatus().is2xxSuccessful();
	}

}
