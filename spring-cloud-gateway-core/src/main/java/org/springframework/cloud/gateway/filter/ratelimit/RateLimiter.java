/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.Collections;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.StatefulConfigurable;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public interface RateLimiter<C> extends StatefulConfigurable<C> {

	Mono<Response> isAllowed(String routeId, String id);

	class Response {

		private final boolean allowed;

		private final long tokensRemaining;

		private final Map<String, String> headers;

		public Response(boolean allowed, Map<String, String> headers) {
			this.allowed = allowed;
			this.tokensRemaining = -1;
			Assert.notNull(headers, "headers may not be null");
			this.headers = headers;
		}

		public boolean isAllowed() {
			return allowed;
		}

		public Map<String, String> getHeaders() {
			return Collections.unmodifiableMap(headers);
		}

		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer("Response{");
			sb.append("allowed=").append(allowed);
			sb.append(", headers=").append(headers);
			sb.append(", tokensRemaining=").append(tokensRemaining);
			sb.append('}');
			return sb.toString();
		}

	}

}
