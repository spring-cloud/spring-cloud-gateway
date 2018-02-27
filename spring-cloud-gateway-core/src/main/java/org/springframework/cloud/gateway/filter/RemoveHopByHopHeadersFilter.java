package org.springframework.cloud.gateway.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;

@ConfigurationProperties("spring.cloud.gateway.filter.remove-hop-by-hop")
public class RemoveHopByHopHeadersFilter implements HttpHeadersFilter, Ordered {

	public static final Set<String> HEADERS_REMOVED_ON_REQUEST =
			new HashSet<>(Arrays.asList(
					"connection",
					"keep-alive",
					"transfer-encoding",
					"te",
					"trailer",
					"proxy-authorization",
					"proxy-authenticate",
					"x-application-context",
					"upgrade"
					// these two are not listed in https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-7.1.3
					//"proxy-connection",
					// "content-length",
					));

	private int order = Ordered.LOWEST_PRECEDENCE;

	private Set<String> headers = HEADERS_REMOVED_ON_REQUEST;

	public Set<String> getHeaders() {
		return headers;
	}

	public void setHeaders(Set<String> headers) {
		this.headers = headers;
	}

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public HttpHeaders filter(HttpHeaders original) {
		HttpHeaders filtered = new HttpHeaders();
		List<String> connection = original.getConnection();
		Set<String> toFilter = new HashSet<>(connection);
		toFilter.addAll(this.headers);

		original.entrySet().stream()
				.filter(entry -> !toFilter.contains(entry.getKey().toLowerCase()))
				.forEach(entry -> filtered.addAll(entry.getKey(), entry.getValue()));

		return filtered;
	}
}
