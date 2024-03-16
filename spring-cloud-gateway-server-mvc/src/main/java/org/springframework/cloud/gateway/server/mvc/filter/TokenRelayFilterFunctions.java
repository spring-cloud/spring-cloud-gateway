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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.security.Principal;

import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.getApplicationContext;

public abstract class TokenRelayFilterFunctions {

	private TokenRelayFilterFunctions() {
	}

	@Shortcut
	public static HandlerFilterFunction<ServerResponse, ServerResponse> tokenRelay() {
		return (request, next) -> {
			Principal principle = request.servletRequest().getUserPrincipal();
			if (principle instanceof OAuth2AuthenticationToken token) {
				String clientRegistrationId = token.getAuthorizedClientRegistrationId();
				OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
						.withClientRegistrationId(clientRegistrationId).principal(token).build();
				OAuth2AuthorizedClientManager clientManager = getApplicationContext(request)
						.getBean(OAuth2AuthorizedClientManager.class);
				OAuth2AuthorizedClient authorizedClient = clientManager.authorize(authorizeRequest);
				OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
				ServerRequest modified = ServerRequest.from(request)
						.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken.getTokenValue())).build();
				return next.handle(modified);
			}
			return next.handle(request);
		};
	}

	public static class FilterSupplier extends SimpleFilterSupplier {

		public FilterSupplier() {
			super(TokenRelayFilterFunctions.class);
		}

	}

}
