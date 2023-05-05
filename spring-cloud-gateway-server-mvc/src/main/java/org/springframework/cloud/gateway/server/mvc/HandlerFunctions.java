/*
 * Copyright 2023 the original author or authors.
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

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

public class HandlerFunctions {
	public static HandlerFunction<ServerResponse> http(String uri) {
		return http(URI.create(uri));
	}

	public static HandlerFunction<ServerResponse> http(URI uri) {
		return new ProxyHandlerFunction(req -> uri);
	}

	public static HandlerFunction<ServerResponse> http(URIResolver uriResolver) {
		return new ProxyHandlerFunction(uriResolver);
	}

	interface URIResolver extends Function<ServerRequest, URI> {

	}

	static class ProxyHandlerFunction implements HandlerFunction<ServerResponse> {

		private RestTemplate restTemplate;

		private final URIResolver uriResolver;

		ProxyHandlerFunction(URIResolver uriResolver) {
			this.uriResolver = uriResolver;
		}


		@Override
		public ServerResponse handle(ServerRequest request) {
			RestTemplate restTemplate = getRestTemplate(request);
			if (restTemplate != null) {
				URI uri = uriResolver.apply(request);
				boolean encoded = containsEncodedQuery(request.uri());
				URI url = UriComponentsBuilder.fromUri(request.uri())
						// .uri(routeUri)
						.scheme(uri.getScheme()).host(uri.getHost()).port(uri.getPort()).build(encoded).toUri();

				RequestEntity<Void> entity = RequestEntity.method(request.method(), url)
						.headers(request.headers().asHttpHeaders())
						.build();
				ResponseEntity<Object> response = restTemplate.exchange(entity, Object.class);
				return ServerResponse.status(response.getStatusCode())
						.headers(httpHeaders -> httpHeaders.putAll(response.getHeaders()))
						.body(response.getBody());
			}
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		private RestTemplate getRestTemplate(ServerRequest request) {
			if (this.restTemplate == null) {
				this.restTemplate = getBean(request, RestTemplate.class);
			}
			return this.restTemplate;
		}

	}

	static ApplicationContext getApplicationContext(ServerRequest request) {
		Optional<Object> contextAttr = request.attribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (contextAttr.isEmpty()) {
			throw new IllegalStateException("No Application Context in request attributes");
		}
		return  (ApplicationContext) contextAttr.get();
	}

	static <T> T getBean(ServerRequest request, Class<T> type) {
		return getApplicationContext(request).getBean(type);
	}

	static boolean containsEncodedQuery(URI uri) {
		boolean encoded = (uri.getRawQuery() != null && uri.getRawQuery().contains("%"))
				|| (uri.getRawPath() != null && uri.getRawPath().contains("%"));

		// Verify if it is really fully encoded. Treat partial encoded as unencoded.
		if (encoded) {
			try {
				UriComponentsBuilder.fromUri(uri).build(true);
				return true;
			}
			catch (IllegalArgumentException ignored) {
				/*if (log.isTraceEnabled()) {
					log.trace("Error in containsEncodedParts", ignored);
				}*/
			}

			return false;
		}

		return encoded;
	}
}
