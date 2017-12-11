package org.springframework.cloud.gateway.filter;

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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
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
		verify(loadBalancerClient).reconstructURI(eq(serviceInstance), urlArgumentCaptor.capture());

		URI uri = urlArgumentCaptor.getValue();
		assertThat(uri).isNotNull();
		assertThat(uri.toString()).isEqualTo("loadbalancerclient.org");

		verifyNoMoreInteractions(loadBalancerClient);

		assertThat((URI)exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)).isEqualTo(requestUrl);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}
}
