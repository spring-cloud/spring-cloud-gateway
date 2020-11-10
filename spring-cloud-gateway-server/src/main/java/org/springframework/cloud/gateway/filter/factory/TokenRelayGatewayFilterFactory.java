/*
 * Copyright 2002-2018 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Joe Grandja
 */
public class TokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	private final ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManagerProvider;

	public TokenRelayGatewayFilterFactory(ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManagerProvider) {
		super(Object.class);
		this.clientManagerProvider = clientManagerProvider;
	}

	public GatewayFilter apply() {
		return apply((Object) null);
	}

	@Override
	public GatewayFilter apply(Object config) {
		return (exchange, chain) -> exchange.getPrincipal()
				// .log("token-relay-filter")
				.filter(principal -> principal instanceof OAuth2AuthenticationToken)
				.cast(OAuth2AuthenticationToken.class)
				.flatMap(authentication -> authorizedClient(exchange, authentication))
				.map(OAuth2AuthorizedClient::getAccessToken).map(token -> withBearerAuth(exchange, token))
				// TODO: adjustable behavior if empty
				.defaultIfEmpty(exchange).flatMap(chain::filter);
	}

	private Mono<OAuth2AuthorizedClient> authorizedClient(ServerWebExchange exchange,
			OAuth2AuthenticationToken oauth2Authentication) {
		String clientRegistrationId = oauth2Authentication.getAuthorizedClientRegistrationId();
		OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
				.principal(oauth2Authentication).build();
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
		return exchange.mutate().request(r -> r.headers(headers -> headers.setBearerAuth(accessToken.getTokenValue())))
				.build();
	}

}
