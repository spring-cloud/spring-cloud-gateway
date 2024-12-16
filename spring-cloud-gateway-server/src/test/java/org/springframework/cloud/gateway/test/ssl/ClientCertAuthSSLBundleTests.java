package org.springframework.cloud.gateway.test.ssl;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.netty.http.client.HttpClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("client-auth-ssl-bundle")
public class ClientCertAuthSSLBundleTests extends SingleCertSSLTests {
	@Autowired
	private SslBundles sslBundles;

	@BeforeEach
	public void setup() throws Exception {
		final var sslBundle = sslBundles.getBundle("scg-keystore-with-different-key-password");
		final var sslContext = SslContextBuilder.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE)
				.keyManager(sslBundle.getManagers().getKeyManagerFactory())
				.build();
		HttpClient httpClient = HttpClient.create().secure(ssl -> ssl.sslContext(sslContext));
		setup(new ReactorClientHttpConnector(httpClient), "https://localhost:" + port);
	}
}
