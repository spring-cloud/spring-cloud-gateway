package org.springframework.cloud.gateway.filter.factory;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Spencer Gibb
 */
public class RewritePathFilterFactoryTests {

	@Test
	public void rewritePathFilterWorks() {
		testRewriteFilter("/foo", "/baz", "/foo/bar", "/baz/bar");
	}

	@Test
	public void rewritePathFilterWithNamedGroupWorks() {
		testRewriteFilter("/foo/(?<id>\\d.*)", "/bar/baz/$\\{id}", "/foo/123", "/bar/baz/123");
	}

	private void testRewriteFilter(String regex, String replacement, String actualPath, String expectedPath) {
		GatewayFilter filter = new RewritePathFilterFactory().apply(regex, new String[]{replacement});

		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost"+ actualPath)
				.build();

		DefaultServerWebExchange exchange = new DefaultServerWebExchange(request, new MockServerHttpResponse());

		WebFilterChain filterChain = mock(WebFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		Assertions.assertThat(webExchange.getRequest().getURI().getPath()).isEqualTo(expectedPath);
	}
}
