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

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.WebFilter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * @author Spencer Gibb
 */
public class SetStatusWebFilterFactory implements WebFilterFactory {

	public static final String STATUS_KEY = "status";

	@Override
	public List<String> argNames() {
		return Arrays.asList(STATUS_KEY);
	}

	@Override
	public WebFilter apply(Tuple args) {
		String status = args.getRawString(STATUS_KEY);
		final HttpStatus httpStatus = ServerWebExchangeUtils.parse(status);

		return (exchange, chain) -> {

			// option 1 (runs in filter order)
			/*exchange.getResponse().beforeCommit(() -> {
				exchange.getResponse().setStatusCode(finalStatus);
				return Mono.empty();
			});
			return chain.filter(exchange);*/

			// option 2 (runs in reverse filter order)
			return chain.filter(exchange).then(Mono.defer(() -> {
				// check not really needed, since it is guarded in setStatusCode, but it's a good example
				if (!exchange.getResponse().isCommitted()) {
					setResponseStatus(exchange, httpStatus);
				}
				return Mono.empty();
			}));
		};
	}

}
