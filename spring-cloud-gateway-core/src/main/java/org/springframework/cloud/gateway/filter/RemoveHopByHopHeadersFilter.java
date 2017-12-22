package org.springframework.cloud.gateway.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;

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

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public HttpHeaders filter(HttpHeaders original) {
		HttpHeaders filtered = new HttpHeaders();
		List<String> connection = original.getConnection();
		Set<String> toFilter = new HashSet<>(connection);
		toFilter.addAll(HEADERS_REMOVED_ON_REQUEST);

		original.entrySet().stream()
				.filter(entry -> !toFilter.contains(entry.getKey().toLowerCase()))
				.forEach(entry -> filtered.addAll(entry.getKey(), entry.getValue()));

		return filtered;
	}
}
