/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.support.tagsprovider;

import io.micrometer.core.instrument.Tags;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Ingyu Hwang
 */
public class GatewayHttpTagsProvider implements GatewayTagsProvider {

	@Override
	public Tags apply(ServerWebExchange exchange) {
		String outcome = "CUSTOM";
		String status = "CUSTOM";
		String httpStatusCodeStr = "NA";

		String httpMethod = exchange.getRequest().getMethodValue();

		// a non standard HTTPS status could be used. Let's be defensive here
		// it needs to be checked for first, otherwise the delegate response
		// who's status DIDN'T change, will be used
		if (exchange.getResponse() instanceof AbstractServerHttpResponse) {
			Integer statusInt = ((AbstractServerHttpResponse) exchange.getResponse()).getRawStatusCode();
			if (statusInt != null) {
				status = String.valueOf(statusInt);
				httpStatusCodeStr = status;
				HttpStatus resolved = HttpStatus.resolve(statusInt);
				if (resolved != null) {
					// this is not a CUSTOM status, so use series here.
					outcome = resolved.series().name();
					status = resolved.name();
				}
			}
		}
		else {
			HttpStatus statusCode = exchange.getResponse().getStatusCode();
			if (statusCode != null) {
				httpStatusCodeStr = String.valueOf(statusCode.value());
				outcome = statusCode.series().name();
				status = statusCode.name();
			}
		}

		return Tags.of("outcome", outcome, "status", status, "httpStatusCode", httpStatusCodeStr, "httpMethod",
				httpMethod);
	}

}
