/*
 * Copyright 2002-2023 the original author or authors.
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

import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Joe Grandja
 * @author Steve Riesenberg
 */
public class TokenRelayGatewayFilterFactory
		extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

	private final ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManagerProvider;

	public TokenRelayGatewayFilterFactory(ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManagerProvider) {
		super(NameConfig.class);
		this.clientManagerProvider = clientManagerProvider;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList(NAME_KEY);
	}

	public GatewayFilter apply() {
		return apply((NameConfig) null);
	}

	@Override
	public GatewayFilter apply(NameConfig config) {
		String defaultClientRegistrationId = (config == null) ? null : config.getName();
		return (exchange, chain) -> exchange.getPrincipal()
			// .log("token-relay-filter")
			.filter(principal -> principal instanceof Authentication)
			.cast(Authentication.class)
			.flatMap(principal -> authorizationRequest(defaultClientRegistrationId, principal))
			.flatMap(this::authorizedClient)
			.map(OAuth2AuthorizedClient::getAccessToken)
			.map(token -> withBearerAuth(exchange, token))
			// TODO: adjustable behavior if empty
			.defaultIfEmpty(exchange)
			.flatMap(chain::filter);
	}

	private Mono<OAuth2AuthorizeRequest> authorizationRequest(String defaultClientRegistrationId,
			Authentication principal) {
		String clientRegistrationId = defaultClientRegistrationId;
		if (clientRegistrationId == null && principal instanceof OAuth2AuthenticationToken) {
			clientRegistrationId = ((OAuth2AuthenticationToken) principal).getAuthorizedClientRegistrationId();
		}
		return Mono.justOrEmpty(clientRegistrationId)
			.map(OAuth2AuthorizeRequest::withClientRegistrationId)
			.map(builder -> builder.principal(principal).build());
	}

	private Mono<OAuth2AuthorizedClient> authorizedClient(OAuth2AuthorizeRequest request) {
		ReactiveOAuth2AuthorizedClientManager clientManager = clientManagerProvider.getIfAvailable();
		if (clientManager == null) {
			return Mono.error(new IllegalStateException(
					"No ReactiveOAuth2AuthorizedClientManager bean was found. Did you include the "
							+ "org.springframework.boot:spring-boot-starter-oauth2-client dependency?"));
		}
		// TODO: use Mono.defer() for request above?
		return clientManager.authorize(request);
	}

	private ServerWebExchange withBearerAuth(ServerWebExchange exchange, OAuth2AccessToken accessToken) {
		return exchange.mutate()
			.request(r -> r.headers(headers -> headers.setBearerAuth(accessToken.getTokenValue())))
			.build();
	}

}
