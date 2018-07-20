package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * This filter blocks the request, if the request size is more than the
 * permissible size.The default request size is 5 MB
 *
 * @author Arpan
 */
public class RequestSizeGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestSizeGatewayFilterFactory.RequestSizeConfig> {

	private static String PREFIX = "kMGTPE";
	private static String ERROR = "Request size is larger than permissible limit."
			+ " Request size is %s where permissible limit is %s";

	public RequestSizeGatewayFilterFactory() {
		super(RequestSizeGatewayFilterFactory.RequestSizeConfig.class);
	}

	@Override
	public GatewayFilter apply(RequestSizeGatewayFilterFactory.RequestSizeConfig requestSizeConfig) {
		requestSizeConfig.validate();
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest();
			String contentLength = request.getHeaders().getFirst("content-length");
			if (!StringUtils.isEmpty(contentLength)) {
				Long currentRequestSize = Long.valueOf(contentLength);
				if (currentRequestSize > requestSizeConfig.getMaxSize()) {
					exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
					exchange.getResponse().getHeaders().add("errorMessage",
							getErrorMessage(currentRequestSize, requestSizeConfig.getMaxSize()));
					return exchange.getResponse().setComplete();
				}
			}
			return chain.filter(exchange);
		};
	}

	public static class RequestSizeConfig {

		// 5 MB is the default request size
		private Long maxSize = 5000000L;

		public RequestSizeGatewayFilterFactory.RequestSizeConfig setMaxSize(Long maxSize) {
			this.maxSize = maxSize;
			return this;
		}

		public Long getMaxSize() {
			return maxSize;
		}

		public void validate() {
			Assert.isTrue(this.maxSize != null && this.maxSize > 0, "maxSize must be greater than 0");
			Assert.isInstanceOf(Long.class, maxSize, "maxSize must be a number");
		}
	}

	private static String getErrorMessage(Long currentRequestSize, Long maxSize) {
		return String.format(ERROR, getReadableByteCount(currentRequestSize), getReadableByteCount(maxSize));
	}

	private static String getReadableByteCount(long bytes) {
		int unit = 1000;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = Character.toString(PREFIX.charAt(exp - 1));
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}