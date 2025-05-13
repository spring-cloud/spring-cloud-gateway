/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory.NameConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.context.SecurityContextServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Spencer Gibb
 *
 */
public class TokenRelayGatewayFilterFactoryTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

	private MockServerHttpRequest request;

	private MockServerWebExchange mockExchange;

	private GatewayFilterChain filterChain;

	private ObjectProvider<ReactiveOAuth2AuthorizedClientManager> objectProvider;

	public TokenRelayGatewayFilterFactoryTests() {
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	public void init() {
		request = MockServerHttpRequest.get("/hello").build();
		mockExchange = MockServerWebExchange.from(request);
		filterChain = mock(GatewayFilterChain.class);
		when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

		authorizedClientManager = mock(ReactiveOAuth2AuthorizedClientManager.class);
		objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(authorizedClientManager);
	}

	@AfterEach
	public void after() {
	}

	@Test
	public void emptyPrincipal() {
		GatewayFilter filter = new TokenRelayGatewayFilterFactory(objectProvider).apply();
		filter.filter(mockExchange, filterChain).block(TIMEOUT);
		assertThat(request.getHeaders()).doesNotContainKeys(HttpHeaders.AUTHORIZATION);
	}

	@Test
	public void whenPrincipalExistsAuthorizationHeaderAdded() {
		OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
		when(accessToken.getTokenValue()).thenReturn("mytoken");

		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("myregistrationid")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("myclientid")
			.tokenUri("mytokenuri")
			.build();
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(clientRegistration, "joe", accessToken);

		when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
			.thenReturn(Mono.just(authorizedClient));

		OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken(mock(OAuth2User.class),
				Collections.emptyList(), "myId");
		SecurityContextImpl securityContext = new SecurityContextImpl(authenticationToken);
		SecurityContextServerWebExchange exchange = new SecurityContextServerWebExchange(mockExchange,
				Mono.just(securityContext));

		GatewayFilter filter = new TokenRelayGatewayFilterFactory(objectProvider).apply();
		filter.filter(exchange, filterChain).block(TIMEOUT);

		ArgumentCaptor<ServerWebExchange> sweCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
		verify(filterChain).filter(sweCaptor.capture());
		ServerWebExchange mutatedServerWebExchange = sweCaptor.getValue();

		assertThat(mutatedServerWebExchange.getRequest().getHeaders()).containsEntry(HttpHeaders.AUTHORIZATION,
				Collections.singletonList("Bearer mytoken"));

		ArgumentCaptor<OAuth2AuthorizeRequest> authorizeRequestCaptor = ArgumentCaptor
			.forClass(OAuth2AuthorizeRequest.class);
		verify(authorizedClientManager).authorize(authorizeRequestCaptor.capture());

		OAuth2AuthorizeRequest authorizeRequest = authorizeRequestCaptor.getValue();
		assertThat(authorizeRequest.getClientRegistrationId())
			.isEqualTo(authenticationToken.getAuthorizedClientRegistrationId());
		assertThat(authorizeRequest.getClientRegistrationId()).isNotEqualTo(clientRegistration.getRegistrationId());
	}

	@Test
	public void whenClientRegistrationIdConfiguredAuthorizationHeaderAdded() {
		OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
		when(accessToken.getTokenValue()).thenReturn("mytoken");

		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("myregistrationid")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("myclientid")
			.tokenUri("mytokenuri")
			.build();
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(clientRegistration, "steve", accessToken);

		when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
			.thenReturn(Mono.just(authorizedClient));

		OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken(mock(OAuth2User.class),
				Collections.emptyList(), "myId");
		SecurityContextImpl securityContext = new SecurityContextImpl(authenticationToken);
		SecurityContextServerWebExchange exchange = new SecurityContextServerWebExchange(mockExchange,
				Mono.just(securityContext));

		NameConfig config = new NameConfig();
		config.setName(clientRegistration.getRegistrationId());

		GatewayFilter filter = new TokenRelayGatewayFilterFactory(objectProvider).apply(config);
		filter.filter(exchange, filterChain).block(TIMEOUT);

		ArgumentCaptor<ServerWebExchange> sweCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
		verify(filterChain).filter(sweCaptor.capture());
		ServerWebExchange mutatedServerWebExchange = sweCaptor.getValue();

		assertThat(mutatedServerWebExchange.getRequest().getHeaders()).containsEntry(HttpHeaders.AUTHORIZATION,
				Collections.singletonList("Bearer mytoken"));

		ArgumentCaptor<OAuth2AuthorizeRequest> authorizeRequestCaptor = ArgumentCaptor
			.forClass(OAuth2AuthorizeRequest.class);
		verify(authorizedClientManager).authorize(authorizeRequestCaptor.capture());

		OAuth2AuthorizeRequest authorizeRequest = authorizeRequestCaptor.getValue();
		assertThat(authorizeRequest.getClientRegistrationId()).isEqualTo(clientRegistration.getRegistrationId());
		assertThat(authorizeRequest.getClientRegistrationId())
			.isNotEqualTo(authenticationToken.getAuthorizedClientRegistrationId());
	}

	@Test
	public void principalIsNotOAuth2AuthenticationToken() {
		SecurityContextImpl securityContext = new SecurityContextImpl(new TestingAuthenticationToken("my", null));
		SecurityContextServerWebExchange exchange = new SecurityContextServerWebExchange(mockExchange,
				Mono.just(securityContext));

		GatewayFilter filter = new TokenRelayGatewayFilterFactory(objectProvider).apply();
		filter.filter(exchange, filterChain).block(TIMEOUT);

		assertThat(request.getHeaders()).doesNotContainKeys(HttpHeaders.AUTHORIZATION);
	}

	@Test
	public void whenPrincipalIsNotOAuth2AuthenticationTokenAndClientRegistrationIdConfiguredAuthorizationHeaderAdded() {
		OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
		when(accessToken.getTokenValue()).thenReturn("mytoken");

		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("myregistrationid")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("myclientid")
			.tokenUri("mytokenuri")
			.build();
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(clientRegistration, "steve", accessToken);

		when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
			.thenReturn(Mono.just(authorizedClient));

		Authentication authenticationToken = new TestingAuthenticationToken("my", null);
		SecurityContextImpl securityContext = new SecurityContextImpl(authenticationToken);
		SecurityContextServerWebExchange exchange = new SecurityContextServerWebExchange(mockExchange,
				Mono.just(securityContext));

		NameConfig config = new NameConfig();
		config.setName(clientRegistration.getRegistrationId());

		GatewayFilter filter = new TokenRelayGatewayFilterFactory(objectProvider).apply(config);
		filter.filter(exchange, filterChain).block(TIMEOUT);

		ArgumentCaptor<ServerWebExchange> sweCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
		verify(filterChain).filter(sweCaptor.capture());
		ServerWebExchange mutatedServerWebExchange = sweCaptor.getValue();
		assertThat(mutatedServerWebExchange.getRequest().getHeaders()).containsEntry(HttpHeaders.AUTHORIZATION,
				Collections.singletonList("Bearer mytoken"));

		ArgumentCaptor<OAuth2AuthorizeRequest> authorizeRequestCaptor = ArgumentCaptor
			.forClass(OAuth2AuthorizeRequest.class);
		verify(authorizedClientManager).authorize(authorizeRequestCaptor.capture());

		OAuth2AuthorizeRequest authorizeRequest = authorizeRequestCaptor.getValue();
		assertThat(authorizeRequest.getClientRegistrationId()).isEqualTo(clientRegistration.getRegistrationId());
	}

}
