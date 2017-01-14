package org.springframework.cloud.gateway.filter.route;

import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.handler.predicate.UrlRoutePredicate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetPathRouteFilterTests {

	@Test
	public void rewritePathFilterWorks() {
		HashMap<String, String> variables = new HashMap<>();
		testRewriteFilter("/baz/bar", "/foo/bar", "/baz/bar", variables);
	}

	@Test
	public void setPathFilterWithTemplateVarsWorks() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "123");
		testRewriteFilter("/bar/baz/{id}", "/foo/123", "/bar/baz/123", variables);
	}

	private void testRewriteFilter(String template, String actualPath, String expectedPath, HashMap<String, String> variables) {
		WebFilter filter = new SetPathRouteFilter().apply(template, new String[]{});

		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost"+ actualPath)
				.build();

		DefaultServerWebExchange exchange = new DefaultServerWebExchange(request, new MockServerHttpResponse());
		exchange.getAttributes().put(UrlRoutePredicate.URL_PREDICATE_VARS_ATTR, variables);

		WebFilterChain filterChain = mock(WebFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		Assertions.assertThat(webExchange.getRequest().getURI().getPath()).isEqualTo(expectedPath);
	}
}
