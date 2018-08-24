/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceSuppliers;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * @author Spencer Gibb
 * @author Tim Ysewyn
 */
@RunWith(MockitoJUnitRunner.class)
public class ReactorLoadBalancerClientFilterTests {

	private ServerWebExchange exchange;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private LoadBalancerClientFactory clientFactory;

	@InjectMocks
	private ReactiveLoadBalancerClientFilter filter;

	@Before
	public void setup() {
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/mypath").build());
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlIsMissing() {
		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyZeroInteractions(clientFactory);
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyZeroInteractions(clientFactory);
	}

	@Test(expected = NotFoundException.class)
	public void shouldThrowExceptionWhenNoServiceInstanceIsFound() {
		URI uri = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		filter.filter(exchange, chain).block();
	}

	@Test
	public void shouldFilter() {
		URI url = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, url);

		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice", "localhost", 8080, true);

		when(clientFactory.getInstance("myservice", ReactorLoadBalancer.class, ServiceInstance.class))
				.thenReturn(new RoundRobinLoadBalancer("myservice",
						ServiceInstanceSuppliers.toProvider("myservice", serviceInstance),
						-1));

		when(chain.filter(exchange)).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		assertThat((LinkedHashSet<URI>)exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(url);

		verify(clientFactory).getInstance("myservice", ReactorLoadBalancer.class, ServiceInstance.class);

		verifyNoMoreInteractions(clientFactory);

		assertThat((URI)exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)).isEqualTo(URI.create("https://localhost:8080/mypath"));

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

	@Ignore //FIXME: 2.1.0
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

		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyZeroInteractions(clientFactory);
	}

	@Ignore //FIXME: 2.1.0
	@Test
	public void shouldSelectSpecifiedServer() {
		/*URI uri1 = UriComponentsBuilder.fromUriString("lb://myservice").port(11111).build().toUri();
		URI uri2 = UriComponentsBuilder.fromUriString("lb://myservice").port(22222).build().toUri();

		SpringClientFactory clientFactory = mock(SpringClientFactory.class);
		ILoadBalancer loadBalancer = mock(ILoadBalancer.class);
		when(clientFactory.getLoadBalancerContext("myservice")).thenReturn(new RibbonLoadBalancerContext(loadBalancer));
		when(clientFactory.getLoadBalancer("myservice")).thenReturn(loadBalancer);

		when(loadBalancer.chooseServer("11111")).thenReturn(new Server("myservice-host1", 8081));
		when(loadBalancer.chooseServer("22222")).thenReturn(new Server("myservice-host2", 8081));

		LoadBalancerClient loadBalancerClient = new RibbonLoadBalancerClient(clientFactory) {
			private String loadBalancerKey;
			public ServiceInstance choose(String serviceId) {
				String[] strings = serviceId.split("<<>>");
				loadBalancerKey = strings[1];
				return super.choose(strings[0]);
			}
			protected Server getServer(ILoadBalancer loadBalancer) {
				return loadBalancer == null ? null : loadBalancer.chooseServer(StringUtils.isEmpty(loadBalancerKey) ? "default" : loadBalancerKey);
			}
		};

		LoadBalancerClientFilter loadBalancerClientFilter = new LoadBalancerClientFilter(loadBalancerClient) {
			protected ServiceInstance choose(ServerWebExchange exchange) {
				URI attribute = (URI) exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
				return loadBalancer.choose(attribute.getHost() + "<<>>" + attribute.getPort());
			}
		};

		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);

		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri1);
		loadBalancerClientFilter.filter(exchange, chain);
		assertThat(((URI)exchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR)).getHost()).isEqualTo("myservice-host1");

		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri2);
		loadBalancerClientFilter.filter(exchange, chain);
		assertThat(((URI)exchange.getAttributes().get(GATEWAY_REQUEST_URL_ATTR)).getHost()).isEqualTo("myservice-host2");*/
	}

	private ServerWebExchange testFilter(MockServerHttpRequest request, URI uri) {
		return testFilter(MockServerWebExchange.from(request), uri);
	}

    private ServerWebExchange testFilter(ServerWebExchange exchange, URI uri) {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		when(clientFactory.getInstance("service1", ReactorLoadBalancer.class, ServiceInstance.class))
				.thenReturn(new RoundRobinLoadBalancer("service1",
						ServiceInstanceSuppliers.toProvider("service1",
								new DefaultServiceInstance("service1", "service1-host1", 8081, false)),
						-1));

		ReactiveLoadBalancerClientFilter filter = new ReactiveLoadBalancerClientFilter(clientFactory);
		filter.filter(exchange, chain).block();

		return captor.getValue();
	}
}
