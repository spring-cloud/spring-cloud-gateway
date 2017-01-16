package org.springframework.cloud.gateway.filter.route;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.WebFilter;

/**
 * @author Spencer Gibb
 */
public class AddRequestParameterRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String parameter, String[] args) {
		validate(args, 1);

		//TODO: caching can happen here
		return (exchange, chain) -> {

			URI uri = exchange.getRequest().getURI();
			StringBuilder query = new StringBuilder();
			String originalQuery = uri.getQuery();

			if (StringUtils.hasText(originalQuery)) {
				query.append(originalQuery);
				if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
					query.append('&');
				}
			}

			//TODO urlencode?
			query.append(parameter);
			query.append('=');
			query.append(args[0]);

			ServerHttpRequest request = new QueryParamServerHttpRequestBuilder(exchange.getRequest())
					.query(query.toString())
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}

	class QueryParamServerHttpRequestBuilder implements ServerHttpRequest.Builder {

		private final ServerHttpRequest delegate;
		private String query;

		public QueryParamServerHttpRequestBuilder(ServerHttpRequest delegate) {
			Assert.notNull(delegate, "ServerHttpRequest delegate is required");
			this.delegate = delegate;
		}


		@Override
		public ServerHttpRequest.Builder method(HttpMethod httpMethod) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ServerHttpRequest.Builder path(String path) {
			throw new UnsupportedOperationException();
		}

		public ServerHttpRequest.Builder query(String query) {
			this.query = query;
			return this;
		}

		@Override
		public ServerHttpRequest.Builder contextPath(String contextPath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ServerHttpRequest.Builder header(String key, String value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ServerHttpRequest build() {
			URI uri = null;
			if (this.query != null) {
				uri = this.delegate.getURI();
				try {
					uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
							uri.getPath(), this.query, uri.getFragment());
				} catch (URISyntaxException ex) {
					throw new IllegalStateException("Invalid URI query: \"" + this.query + "\"");
				}
			}
			return new MutativeDecorator(this.delegate, uri);
		}


		/**
		 * An immutable wrapper of a request returning property overrides -- given
		 * to the constructor -- or original values otherwise.
		 */
		private class MutativeDecorator extends ServerHttpRequestDecorator {

			private final URI uri;

			public MutativeDecorator(ServerHttpRequest delegate, URI uri) {
				super(delegate);

				this.uri = uri;
			}

			@Override
			public HttpMethod getMethod() {
				return super.getMethod();
			}

			@Override
			public URI getURI() {
				return (this.uri != null ? this.uri : super.getURI());
			}

			@Override
			public String getContextPath() {
				return super.getContextPath();
			}

			@Override
			public HttpHeaders getHeaders() {
				return super.getHeaders();
			}
		}

	}
}
