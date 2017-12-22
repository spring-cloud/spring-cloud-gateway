package org.springframework.cloud.gateway.filter;

import org.springframework.http.HttpHeaders;

import java.util.List;

@FunctionalInterface
public interface HttpHeadersFilter {

	HttpHeaders filter(HttpHeaders original);

	static HttpHeaders filter(List<HttpHeadersFilter> filters, HttpHeaders original) {
		HttpHeaders filtered = original;
		if (filters != null) {
			for (HttpHeadersFilter filter: filters) {
				filtered = filter.filter(filtered);
			}
		}
		return filtered;
	}
}
