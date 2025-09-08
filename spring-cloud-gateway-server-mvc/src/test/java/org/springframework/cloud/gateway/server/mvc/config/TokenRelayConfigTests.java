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

package org.springframework.cloud.gateway.server.mvc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.TestAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
@ActiveProfiles("tokenrelay")
public class TokenRelayConfigTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc;

	@BeforeEach
	public void setup() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@WithMockUser
	public void testTokenRelay() throws Exception {
		mvc.perform(get("/bearer"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"authenticated\": true, \"token\": \"test\"}"));
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(TestAutoConfiguration.class)
	public static class TestConfig {

		@Bean
		public OAuth2AuthorizedClientManager authorizedClientManager() {
			OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
			OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
			OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
			when(accessToken.getTokenValue()).thenReturn("test");
			when(client.getAccessToken()).thenReturn(accessToken);
			// The client registration id is set in the token relay filter and must match
			when(manager.authorize(argThat(
					oAuth2AuthorizeRequest -> "token".equals(oAuth2AuthorizeRequest.getClientRegistrationId()))))
				.thenReturn(client);
			return manager;
		}

	}

}
