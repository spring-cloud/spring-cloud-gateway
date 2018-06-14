package org.springframework.cloud.gateway.filter.ratelimit;

import org.springframework.cloud.gateway.support.StatefulConfigurable;

import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

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

		@Deprecated
		public Response(boolean allowed, long tokensRemaining) {
			this.allowed = allowed;
			this.tokensRemaining = tokensRemaining;
			this.headers = Collections.emptyMap();
		}

		public boolean isAllowed() {
			return allowed;
		}

		@Deprecated
		public long getTokensRemaining() {
			return tokensRemaining;
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
