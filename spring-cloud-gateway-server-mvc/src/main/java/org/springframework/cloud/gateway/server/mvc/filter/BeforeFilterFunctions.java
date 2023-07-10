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

import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;

import static org.springframework.util.CollectionUtils.unmodifiableMultiValueMap;

public interface BeforeFilterFunctions {

	static Function<ServerRequest, ServerRequest> addRequestHeader(String name, String... values) {
		return request -> {
			String[] expandedValues = MvcUtils.expandMultiple(request, values);
			return ServerRequest.from(request).header(name, expandedValues).build();
		};
	}

	static Function<ServerRequest, ServerRequest> addRequestParameter(String name, String... values) {
		return request -> {
			String[] expandedValues = MvcUtils.expandMultiple(request, values);
			return ServerRequest.from(request).param(name, expandedValues).build();
		};
	}

	static Function<ServerRequest, ServerRequest> prefixPath(String prefix) {
		final UriTemplate uriTemplate = new UriTemplate(prefix);

		return request -> {
			Map<String, Object> uriVariables = MvcUtils.getUriTemplateVariables(request);
			URI uri = uriTemplate.expand(uriVariables);

			String newPath = uri.getRawPath() + request.uri().getRawPath();

			URI prefixedUri = UriComponentsBuilder.fromUri(request.uri()).replacePath(newPath).build().toUri();
			return ServerRequest.from(request).uri(prefixedUri).build();
		};
	}

	static Function<ServerRequest, ServerRequest> preserveHost() {
		return request -> {
			request.attributes().put(MvcUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, true);
			return request;
		};
	}

	static Function<ServerRequest, ServerRequest> removeRequestHeader(String name) {
		return request -> ServerRequest.from(request).headers(httpHeaders -> httpHeaders.remove(name)).build();
	}

	static Function<ServerRequest, ServerRequest> removeRequestParameter(String name) {
		return request -> {
			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.params());
			queryParams.remove(name);

			// remove from uri
			URI newUri = UriComponentsBuilder.fromUri(request.uri())
					.replaceQueryParams(unmodifiableMultiValueMap(queryParams)).build().toUri();

			// remove resolved params from request
			return ServerRequest.from(request).params(params -> params.remove(name)).uri(newUri).build();
		};
	}

	static Function<ServerRequest, ServerRequest> rewritePath(String regexp, String replacement) {
		String normalizedReplacement = replacement.replace("$\\", "$");
		Pattern pattern = Pattern.compile(regexp);
		return request -> {
			// TODO: original request url
			String path = request.uri().getRawPath();
			String newPath = pattern.matcher(path).replaceAll(normalizedReplacement);

			URI rewrittenUri = UriComponentsBuilder.fromUri(request.uri()).replacePath(newPath).build().toUri();

			ServerRequest modified = ServerRequest.from(request).uri(rewrittenUri).build();

			MvcUtils.setRequestUrl(modified, modified.uri());
			return modified;
		};
	}

	static Function<ServerRequest, ServerRequest> routeId(String routeId) {
		return request -> {
			request.attributes().put(MvcUtils.GATEWAY_ROUTE_ID_ATTR, routeId);
			return request;
		};
	}

	static Function<ServerRequest, ServerRequest> setPath(String path) {
		UriTemplate uriTemplate = new UriTemplate(path);

		return request -> {
			Map<String, Object> uriVariables = MvcUtils.getUriTemplateVariables(request);
			URI uri = uriTemplate.expand(uriVariables);
			String newPath = uri.getRawPath();

			URI prefixedUri = UriComponentsBuilder.fromUri(request.uri()).replacePath(newPath).build().toUri();
			return ServerRequest.from(request).uri(prefixedUri).build();
		};
	}

	static Function<ServerRequest, ServerRequest> setRequestHeader(String name, String value) {
		return request -> {
			String expandedValue = MvcUtils.expand(request, value);
			return ServerRequest.from(request).headers(httpHeaders -> httpHeaders.set(name, expandedValue)).build();
		};
	}

	static Function<ServerRequest, ServerRequest> setRequestHostHeader(String host) {
		return request -> {
			String expandedValue = MvcUtils.expand(request, host);
			ServerRequest modified = ServerRequest.from(request).headers(httpHeaders -> {
				httpHeaders.remove(HttpHeaders.HOST);
				httpHeaders.set(HttpHeaders.HOST, expandedValue);
			}).build();

			// Make sure the header we just set is preserved
			modified.attributes().put(MvcUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, true);
			return modified;
		};
	}

	static Function<ServerRequest, ServerRequest> stripPrefix() {
		return stripPrefix(1);
	}

	static Function<ServerRequest, ServerRequest> stripPrefix(int parts) {
		return request -> {
			// TODO: gateway url attributes
			String path = request.uri().getRawPath();
			// TODO: begin duplicate code from StripPrefixGatewayFilterFactory
			String[] originalParts = StringUtils.tokenizeToStringArray(path, "/");

			// all new paths start with /
			StringBuilder newPath = new StringBuilder("/");
			for (int i = 0; i < originalParts.length; i++) {
				if (i >= parts) {
					// only append slash if this is the second part or greater
					if (newPath.length() > 1) {
						newPath.append('/');
					}
					newPath.append(originalParts[i]);
				}
			}
			if (newPath.length() > 1 && path.endsWith("/")) {
				newPath.append('/');
			}
			// TODO: end duplicate code from StripPrefixGatewayFilterFactory

			URI prefixedUri = UriComponentsBuilder.fromUri(request.uri()).replacePath(newPath.toString()).build()
					.toUri();
			return ServerRequest.from(request).uri(prefixedUri).build();
		};
	}

}
