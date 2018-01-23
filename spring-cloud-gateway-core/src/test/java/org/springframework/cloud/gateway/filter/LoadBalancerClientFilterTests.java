package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerContext;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 * @author Tim Ysewyn
 */
@RunWith(MockitoJUnitRunner.class)
public class LoadBalancerClientFilterTests {

	private ServerWebExchange exchange;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private LoadBalancerClient loadBalancerClient;

	@InjectMocks
	private LoadBalancerClientFilter loadBalancerClientFilter;

	@Before
	public void setup() {
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("loadbalancerclient.org").build());
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlIsMissing() {
		loadBalancerClientFilter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyZeroInteractions(loadBalancerClient);
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		loadBalancerClientFilter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyZeroInteractions(loadBalancerClient);
	}

	@Test(expected = NotFoundException.class)
	public void shouldThrowExceptionWhenNoServiceInstanceIsFound() {
		URI uri = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		loadBalancerClientFilter.filter(exchange, chain);
	}

	@Test
	public void shouldFilter() {
		URI url = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, url);

		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice", "localhost", 8080, true);
		when(loadBalancerClient.choose("myservice")).thenReturn(serviceInstance);

		URI requestUrl = UriComponentsBuilder.fromUriString("https://localhost:8080").build().toUri();
		when(loadBalancerClient.reconstructURI(any(ServiceInstance.class), any(URI.class))).thenReturn(requestUrl);

		loadBalancerClientFilter.filter(exchange, chain);

		assertThat((LinkedHashSet<URI>)exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(url);

		verify(loadBalancerClient).choose("myservice");

		ArgumentCaptor<URI> urlArgumentCaptor = ArgumentCaptor.forClass(URI.class);
		verify(loadBalancerClient).reconstructURI(any(), urlArgumentCaptor.capture());

		URI uri = urlArgumentCaptor.getValue();
		assertThat(uri).isNotNull();
		assertThat(uri.toString()).isEqualTo("loadbalancerclient.org");

		verifyNoMoreInteractions(loadBalancerClient);

		assertThat((URI)exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)).isEqualTo(requestUrl);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}


	@Test
	public void happyPath() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get?a=b")
				.build();

		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange webExchange = testFilter(request, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1")
				.hasParameter("a", "b");
	}

	@Test
	public void noQueryParams() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.build();

		ServerWebExchange webExchange = testFilter(request, URI.create("lb://service1"));
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1");
	}

	@Test
	public void encodedParameters() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode().toUri();

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		URI lbUrl = UriComponentsBuilder.fromUriString("lb://service1?a=b&c=d[]").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(lbUrl).hasParameter("c", "d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	@Test
	public void unencodedParameters() {
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		URI lbUrl = URI.create("lb://service1?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	@Test
	public void happyPathWithAttributeRatherThanScheme() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("ws://localhost/get?a=b")
				.build();

		URI lbUri = URI.create("ws://service1?a=b");

		exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "lb");

		ServerWebExchange webExchange = testFilter(exchange, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("ws").hasHost("service1-host1")
				.hasParameter("a", "b");
	}

	@Test
	public void shouldNotFilterWhenGatewaySchemePrefixAttrIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "xx");

		loadBalancerClientFilter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyZeroInteractions(loadBalancerClient);
	}

	private ServerWebExchange testFilter(MockServerHttpRequest request, URI uri) {
		return testFilter(MockServerWebExchange.from(request), uri);
	}

    private ServerWebExchange testFilter(ServerWebExchange exchange, URI uri) {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		SpringClientFactory clientFactory = mock(SpringClientFactory.class);
		ILoadBalancer loadBalancer = mock(ILoadBalancer.class);

		when(clientFactory.getLoadBalancerContext("service1")).thenReturn(new RibbonLoadBalancerContext(loadBalancer));
		when(clientFactory.getLoadBalancer("service1")).thenReturn(loadBalancer);
		when(loadBalancer.chooseServer(any())).thenReturn(new Server("service1-host1", 8081));

		RibbonLoadBalancerClient client = new RibbonLoadBalancerClient(clientFactory);

		LoadBalancerClientFilter filter = new LoadBalancerClientFilter(client);
		filter.filter(exchange, chain);

		return captor.getValue();
	}
}
