package org.springframework.cloud.gateway.filter.ratelimit;

/**
 * @author Spencer Gibb
 */
public interface RateLimiter {
	Response isAllowed(String id, int replenishRate, int capacity);

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
