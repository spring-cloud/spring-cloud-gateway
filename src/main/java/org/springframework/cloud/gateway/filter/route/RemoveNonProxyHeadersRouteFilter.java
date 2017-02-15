package org.springframework.cloud.gateway.filter.route;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.WebFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Hop-by-hop header fields, which are meaningful only for a single transport-level connection,
 * and are not stored by caches or forwarded by proxies. The following HTTP/1.1 header fields
 * are hop-by-hop header fields:
 * <ul>
 *  <li>Connection
 *  <li>Keep-Alive
 *  <li>Proxy-Authenticate
 *  <li>Proxy-Authorization
 *  <li>TE
 *  <li>Trailer
 *  <li>Transfer-Encoding
 *  <li>Upgrade
 * </ul>
 *
 * See https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-7.1.3
 *
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway.filter.removeNonProxyHeaders")
public class RemoveNonProxyHeadersRouteFilter implements RouteFilter {

	private static final String FAKE_HEADER = "_______force_______";
	public static final String[] DEFAULT_HEADERS_TO_REMOVE = new String[] {"Connection", "Keep-Alive",
			"Proxy-Authenticate", "Proxy-Authorization", "TE", "Trailer", "Transfer-Encoding", "Upgrade"};

	private List<String> headers = Arrays.asList(DEFAULT_HEADERS_TO_REMOVE);

	public List<String> getHeaders() {
		return headers;
	}

	public void setHeaders(List<String> headers) {
		this.headers = headers;
	}

	@Override
	public WebFilter apply(String... args) {
		//TODO: support filter args

		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(FAKE_HEADER, "mutable") //TODO: is there a better way?
					.build();

			request.getHeaders().remove(FAKE_HEADER);

			for (String header : this.headers) {
				request.getHeaders().remove(header);
			}

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
