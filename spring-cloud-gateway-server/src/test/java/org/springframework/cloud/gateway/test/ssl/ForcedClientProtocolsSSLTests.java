package org.springframework.cloud.gateway.test.ssl;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("forced-client-protocols-ssl")
public class ForcedClientProtocolsSSLTests extends SingleCertSSLTests {

}
