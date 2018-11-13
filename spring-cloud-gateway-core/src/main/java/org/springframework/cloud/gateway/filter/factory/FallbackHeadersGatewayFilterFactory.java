package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang.exception.ExceptionUtils.getRootCause;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.HYSTRIX_EXECUTION_EXCEPTION;

/**
 * @author Olga Maciaszek-Sharma
 */
public class FallbackHeadersGatewayFilterFactory extends AbstractGatewayFilterFactory<FallbackHeadersGatewayFilterFactory.Config> {

	public FallbackHeadersGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return singletonList(NAME_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			ServerWebExchange filteredExchange = ofNullable((Throwable) exchange.getAttributes()
					.get(HYSTRIX_EXECUTION_EXCEPTION))
					.map(executionException -> {
						ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
						requestBuilder.header(config.executionExceptionTypeHeaderName, executionException.getClass().getName());
						requestBuilder.header(config.executionExceptionMessageHeaderName, executionException.getMessage());
						ofNullable(getRootCause(executionException)).ifPresent(rootCause -> {
							requestBuilder.header(config.rootCauseExceptionTypeHeaderName, rootCause.getClass().getName());
							requestBuilder.header(config.rootCauseExceptionMessageHeaderName, rootCause.getMessage());
						});
						return exchange.mutate().request(requestBuilder.build()).build();
					}).orElse(exchange);
			return chain.filter(filteredExchange);
		};
	}

	public static class Config {

		private static final String EXECUTION_EXCEPTION_TYPE = "Execution-Exception-Type";
		private static final String EXECUTION_EXCEPTION_MESSAGE = "Execution-Exception-Message";
		private static final String ROOT_CAUSE_EXCEPTION_TYPE = "Root-Cause-Exception-Type";
		private static final String ROOT_CAUSE_EXCEPTION_MESSAGE = "Root-Cause-Exception-Message";

		private String executionExceptionTypeHeaderName = EXECUTION_EXCEPTION_TYPE;
		private String executionExceptionMessageHeaderName = EXECUTION_EXCEPTION_MESSAGE;
		private String rootCauseExceptionTypeHeaderName = ROOT_CAUSE_EXCEPTION_TYPE;
		private String rootCauseExceptionMessageHeaderName = ROOT_CAUSE_EXCEPTION_MESSAGE;

		public String getExecutionExceptionTypeHeaderName() {
			return executionExceptionTypeHeaderName;
		}

		public void setExecutionExceptionTypeHeaderName(String executionExceptionTypeHeaderName) {
			this.executionExceptionTypeHeaderName = executionExceptionTypeHeaderName;
		}

		public String getExecutionExceptionMessageHeaderName() {
			return executionExceptionMessageHeaderName;
		}

		public void setExecutionExceptionMessageHeaderName(String executionExceptionMessageHeaderName) {
			this.executionExceptionMessageHeaderName = executionExceptionMessageHeaderName;
		}

		public String getRootCauseExceptionTypeHeaderName() {
			return rootCauseExceptionTypeHeaderName;
		}

		public void setRootCauseExceptionTypeHeaderName(String rootCauseExceptionTypeHeaderName) {
			this.rootCauseExceptionTypeHeaderName = rootCauseExceptionTypeHeaderName;
		}

		public String getCauseExceptionMessageHeaderName() {
			return rootCauseExceptionMessageHeaderName;
		}

		public void setCauseExceptionMessageHeaderName(String causeExceptionMessageHeaderName) {
			this.rootCauseExceptionMessageHeaderName = causeExceptionMessageHeaderName;
		}
	}
}
