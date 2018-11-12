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

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Sakalya Deshpande
 */
public class ModifyRequestHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<ModifyRequestHeaderGatewayFilterFactory.Config> {

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest();
			HttpHeaders httpHeaders = request.getHeaders();
			String headerValue = httpHeaders.getFirst(config.name);

			if(headerValue.matches(config.regexToBeMatched)){
				request.mutate().headers(headers -> headers.set(config.name,config.value));
			}

			return chain.filter(exchange.mutate().request(request).build());
		};
	}

	public static class Config {
		String name;
		String regexToBeMatched;
		String value;
	}

}
