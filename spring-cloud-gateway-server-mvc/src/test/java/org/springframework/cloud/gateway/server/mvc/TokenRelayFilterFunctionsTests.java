/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenRelayFilterFunctionsTests {

	private final List<HttpMessageConverter<?>> converters = new HttpMessageConverters().getConverters();

	private MockHttpServletRequest request;

	private OAuth2AuthorizedClientManager authorizedClientManager;

	private WebApplicationContext applicationContext;

	private HandlerFilterFunction<ServerResponse, ServerResponse> filter;

	@BeforeEach
	@SuppressWarnings("unchecked")
	public void init() {
		request = MockMvcRequestBuilders.get("/hello").buildRequest(new MockServletContext());
		authorizedClientManager = mock(OAuth2AuthorizedClientManager.class);
		applicationContext = mock(WebApplicationContext.class);
		request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
		when(applicationContext.getBean(OAuth2AuthorizedClientManager.class)).thenReturn(authorizedClientManager);
		filter = TokenRelayFilterFunctions.tokenRelay();
	}

	@Test
	public void emptyPrincipal() throws Exception {
		filter.filter(ServerRequest.create(request, converters), req -> {
			assertThat(req.headers().asHttpHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
			return null;
		});
	}

	@Test
	public void whenPrincipalExistsAuthorizationHeaderAdded() throws Exception {
		OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
		when(accessToken.getTokenValue()).thenReturn("mytoken");

		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("myregistrationid")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("myclientid")
			.tokenUri("mytokenuri")
			.build();
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(clientRegistration, "joe", accessToken);

		when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorizedClient);

		OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken(mock(OAuth2User.class),
				Collections.emptyList(), "myId");
		request.setUserPrincipal(authenticationToken);

		filter.filter(ServerRequest.create(request, converters), req -> {
			assertThat(req.headers().firstHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer mytoken");
			return null;
		});
	}

	@Test
	public void whenDefaultClientRegistrationIdProvidedAuthorizationHeaderAdded() throws Exception {
		OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
		when(accessToken.getTokenValue()).thenReturn("mytoken");

		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("myregistrationid")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("myclientid")
			.tokenUri("mytokenuri")
			.build();
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(clientRegistration, "joe", accessToken);

		when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorizedClient);

		request.setUserPrincipal(new TestingAuthenticationToken("my", null));

		filter = TokenRelayFilterFunctions.tokenRelay("myId");
		filter.filter(ServerRequest.create(request, converters), req -> {
			assertThat(req.headers().firstHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer mytoken");
			return null;
		});
	}

	@Test
	public void principalIsNotOAuth2AuthenticationToken() throws Exception {
		request.setUserPrincipal(new TestingAuthenticationToken("my", null));
		filter.filter(ServerRequest.create(request, converters), req -> {
			assertThat(req.headers().asHttpHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
			return null;
		});
	}

}
