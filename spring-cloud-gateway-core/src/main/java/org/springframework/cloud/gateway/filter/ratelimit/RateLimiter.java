package org.springframework.cloud.gateway.filter.ratelimit;

import org.springframework.cloud.gateway.support.StatefulConfigurable;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public interface RateLimiter<C> extends StatefulConfigurable<C> {

	Mono<Response> isAllowed(String routeId, String id);

	class Response {
		private final boolean allowed;
		private final long tokensRemaining;

		public Response(boolean allowed, long tokensRemaining) {
			this.allowed = allowed;
			this.tokensRemaining = tokensRemaining;
		}

		public boolean isAllowed() {
			return allowed;
		}

		public long getTokensRemaining() {
			return tokensRemaining;
		}

		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer("Response{");
			sb.append("allowed=").append(allowed);
			sb.append(", tokensRemaining=").append(tokensRemaining);
			sb.append('}');
			return sb.toString();
		}
	}
}
