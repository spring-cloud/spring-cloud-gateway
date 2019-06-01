package org.springframework.cloud.gateway.test.ssl;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

import java.io.File;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("client-auth-ssl")
public class ClientCertAuthSSLTests extends SingleCertSSLTests {

	@Value("${spring.cloud.gateway.httpclient.ssl.keyCertChain}")
	private String keyCertChainPath;

	@Value("${spring.cloud.gateway.httpclient.ssl.key}")
	private String keyFilePath;

	@Value("${spring.cloud.gateway.httpclient.ssl.keyPassword}")
	private String keyPassword;

	@Before
	public void setup() {
		try {
			SslContext sslContext = SslContextBuilder.forClient()
					.trustManager(new File(keyCertChainPath))
					.keyManager(new File(keyCertChainPath), new File(keyFilePath),
							keyPassword)
					.build();
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
