package org.springframework.cloud.gateway.filter.factory;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class RewriteRequestHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewriteRequestHeaderGatewayFilterFactory.Config> {

	/**
	 * Regexp key.
	 */
	public static final String REGEXP_KEY = "regexp";

	/**
	 * Replacement key.
	 */
	public static final String REPLACEMENT_KEY = "replacement";

	public RewriteRequestHeaderGatewayFilterFactory() {
		super(RewriteRequestHeaderGatewayFilterFactory.Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, REGEXP_KEY, REPLACEMENT_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				if (exchange.getRequest().getHeaders().containsKey(config.getName())) {
					return chain.filter(exchange.mutate()
							.request(request -> request.headers(httpHeaders -> rewriteHeaders(httpHeaders, config)))
							.build());
				}
				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RewriteRequestHeaderGatewayFilterFactory.this)
						.append("name", config.getName())
						.append("regexp", config.getRegexp())
						.append("replacement", config.getReplacement())
						.toString();
			}
		};
	}

	private void rewriteHeaders(HttpHeaders requestHeaders, Config config) {
		requestHeaders.computeIfPresent(config.getName(), (k, v) -> rewriteHeaders(config, v));
	}

	private List<String> rewriteHeaders(Config config, List<String> headers) {
		return headers.stream()
				.map(header -> rewrite(header, config.getRegexp(), config.getReplacement()))
				.collect(Collectors.toList());
	}

	private String rewrite(String value, String regexp, String replacement) {
		return value.replaceAll(regexp, replacement.replace("$\\", "$"));
	}

	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

		private String regexp;

		private String replacement;

		public String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}

		public String getReplacement() {
			return replacement;
		}

		public Config setReplacement(String replacement) {
			this.replacement = replacement;
			return this;
		}

	}

}
