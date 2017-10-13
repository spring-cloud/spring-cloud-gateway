/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;
import org.springframework.cloud.gateway.filter.GatewayFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Hop-by-hop header fields, which are meaningful only for a single transport-level connection,
 * and are not stored by caches or forwarded by proxies. The following HTTP/1.1 header fields
 * are hop-by-hop header fields:
 * <ul>
 *  <li>Connection
 *  <li>Keep-Alive
 *  <li>Proxy-Authenticate
 *  <li>Proxy-Authorization
 *  <li>TE
 *  <li>Trailer
 *  <li>Transfer-Encoding
 *  <li>Upgrade
 * </ul>
 *
 * See https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-7.1.3
 *
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway.filter.remove-non-proxy-headers")
public class RemoveNonProxyHeadersGatewayFilterFactory implements GatewayFilterFactory {

	public static final String[] DEFAULT_HEADERS_TO_REMOVE = new String[] {"Connection", "Keep-Alive",
			"Proxy-Authenticate", "Proxy-Authorization", "TE", "Trailer", "Transfer-Encoding", "Upgrade"};

	private List<String> headers = Arrays.asList(DEFAULT_HEADERS_TO_REMOVE);

	public List<String> getHeaders() {
		return headers;
	}

	public void setHeaders(List<String> headers) {
		this.headers = headers;
	}

	@Override
	public GatewayFilter apply(Tuple args) {
		//TODO: support filter args

		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.headers(httpHeaders -> {
						for (String header : this.headers) {
							httpHeaders.remove(header);
						}
					})
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
