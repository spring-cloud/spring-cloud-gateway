/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * Tests for {@link ReactiveLoadBalancerClientFilter}.
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 * @author Olga Maciaszek-Sharma
 */
@SuppressWarnings("UnassignedFluxMonoInstance")
@ExtendWith(MockitoExtension.class)
class ReactiveLoadBalancerClientFilterTests {

	private ServerWebExchange exchange;

	private GatewayLoadBalancerProperties properties;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private LoadBalancerClientFactory clientFactory;

	@Mock
	private LoadBalancerProperties loadBalancerProperties;

	@InjectMocks
	private ReactiveLoadBalancerClientFilter filter;

	@BeforeEach
	void setup() {
		properties = new GatewayLoadBalancerProperties();
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/mypath").build());
	}

	@Test
	void shouldNotFilterWhenGatewayRequestUrlIsMissing() {
		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyNoInteractions(clientFactory);
	}

	@Test
	void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyNoInteractions(clientFactory);
	}

	@Test
	void shouldThrowExceptionWhenNoServiceInstanceIsFound() {
		assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> {
			URI uri = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

			filter.filter(exchange, chain).block();
		});
	}

	@SuppressWarnings("unchecked")
	@Test
	void shouldFilter() {
		URI url = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, url);

		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				true);

		RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(
				ServiceInstanceListSuppliers.toProvider("myservice", serviceInstance), "myservice", -1);
		when(clientFactory.getInstance("myservice", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);

		when(chain.filter(exchange)).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		assertThat((LinkedHashSet<URI>) exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(url);

		verify(clientFactory).getInstance("myservice", ReactorServiceInstanceLoadBalancer.class);

		verify(clientFactory).getInstances("myservice", LoadBalancerLifecycle.class);

		verifyNoMoreInteractions(clientFactory);

		assertThat((URI) exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR))
				.isEqualTo(URI.create("https://localhost:8080/mypath"));

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	@Test
	void happyPath() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();

		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange webExchange = testFilter(request, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1").hasParameter("a", "b");
	}

	@Test
	void noQueryParams() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get").build();

		ServerWebExchange webExchange = testFilter(request, URI.create("lb://service1"));
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1");
	}

	@Test
	void encodedParameters() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode()
				.toUri();

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		URI lbUrl = UriComponentsBuilder.fromUriString("lb://service1?a=b&c=d[]").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(lbUrl).hasParameter("c", "d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1").hasParameter("a", "b").hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	@Test
	void unencodedParameters() {
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		URI lbUrl = URI.create("lb://service1?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1").hasParameter("a", "b").hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	@Test
	void happyPathWithAttributeRatherThanScheme() {
		MockServerHttpRequest request = MockServerHttpRequest.get("ws://localhost/get?a=b").build();

		URI lbUri = URI.create("ws://service1?a=b");

		exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "lb");

		ServerWebExchange webExchange = testFilter(exchange, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("ws").hasHost("service1-host1").hasParameter("a", "b");
	}

	@Test
	void shouldNotFilterWhenGatewaySchemePrefixAttrIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "xx");

		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyNoInteractions(clientFactory);
	}

	@Test
	void shouldThrow4O4ExceptionWhenNoServiceInstanceIsFound() {
		URI uri = UriComponentsBuilder.fromUriString("lb://service1").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(
				ServiceInstanceListSuppliers.toProvider("service1"), "service1", -1);
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);
		properties.setUse404(true);
		ReactiveLoadBalancerClientFilter filter = new ReactiveLoadBalancerClientFilter(clientFactory, properties,
				loadBalancerProperties);
		when(chain.filter(exchange)).thenReturn(Mono.empty());
		try {
			filter.filter(exchange, chain).block();
		}
		catch (NotFoundException exception) {
			assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void shouldOverrideSchemeUsingIsSecure() {
		URI url = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		ServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get("https://localhost:9999/mypath").build());
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, url);
		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				false);
		when(clientFactory.getInstance("myservice", ReactorServiceInstanceLoadBalancer.class)).thenReturn(
				new RoundRobinLoadBalancer(ServiceInstanceListSuppliers.toProvider("myservice", serviceInstance),
						"myservice", -1));
		when(chain.filter(exchange)).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		assertThat((LinkedHashSet<URI>) exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(url);
		assertThat((URI) exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR))
				.isEqualTo(URI.create("http://localhost:8080/mypath"));
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	@SuppressWarnings({ "rawtypes" })
	@Test
	void shouldPassRequestToLoadBalancer() {
		String hint = "test";
		when(loadBalancerProperties.getHint()).thenReturn(buildHints(hint));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();
		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange serverWebExchange = mock(ServerWebExchange.class);
		when(serverWebExchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)).thenReturn(lbUri);
		when(serverWebExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR))
				.thenReturn(new LinkedHashSet<>());
		when(serverWebExchange.getRequest()).thenReturn(request);
		RoundRobinLoadBalancer loadBalancer = mock(RoundRobinLoadBalancer.class);
		when(loadBalancer.choose(any(Request.class))).thenReturn(Mono.just(
				new DefaultResponse(new DefaultServiceInstance("myservice1", "service1", "localhost", 8080, false))));
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);
		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(serverWebExchange, chain);

		verify(loadBalancer).choose(argThat((Request passedRequest) -> ((RequestDataContext) passedRequest.getContext())
				.getClientRequest().getUrl().equals(request.getURI())
				&& ((RequestDataContext) passedRequest.getContext()).getHint().equals(hint)));

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void loadBalancerLifecycleCallbacksExecutedForSuccess() {
		LoadBalancerLifecycle lifecycleProcessor = mock(LoadBalancerLifecycle.class);
		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				false);
		ServerWebExchange serverWebExchange = mockExchange(serviceInstance, lifecycleProcessor, false);

		filter.filter(serverWebExchange, chain).subscribe();

		verify(lifecycleProcessor).onStart(any(Request.class));
		verify(lifecycleProcessor).onStartRequest(any(Request.class), any(Response.class));
		verify(lifecycleProcessor).onComplete(argThat(completionContext -> CompletionContext.Status.SUCCESS
				.equals(completionContext.status())
				&& completionContext.getLoadBalancerResponse().getServer().equals(serviceInstance)
				&& HttpMethod.GET.equals(
						((RequestDataContext) completionContext.getLoadBalancerRequest().getContext()).method())));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void loadBalancerLifecycleCallbacksExecutedForDiscard() {
		LoadBalancerLifecycle lifecycleProcessor = mock(LoadBalancerLifecycle.class);
		ServiceInstance serviceInstance = null;
		ServerWebExchange serverWebExchange = mockExchange(serviceInstance, lifecycleProcessor, false);

		filter.filter(serverWebExchange, chain).subscribe();

		verify(lifecycleProcessor).onStart(any(Request.class));
		verify(lifecycleProcessor).onComplete(argThat(completionContext -> CompletionContext.Status.DISCARD
				.equals(completionContext.status())
				&& HttpMethod.GET.equals(
						((RequestDataContext) completionContext.getLoadBalancerRequest().getContext()).method())));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void loadBalancerLifecycleCallbacksExecutedForFailed() {
		LoadBalancerLifecycle lifecycleProcessor = mock(LoadBalancerLifecycle.class);
		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				false);
		ServerWebExchange serverWebExchange = mockExchange(serviceInstance, lifecycleProcessor, true);

		filter.filter(serverWebExchange, chain).subscribe();

		verify(lifecycleProcessor).onStart(any(Request.class));
		verify(lifecycleProcessor).onStartRequest(any(Request.class), any(Response.class));
		verify(lifecycleProcessor).onComplete(argThat(completionContext -> CompletionContext.Status.FAILED
				.equals(completionContext.status())
				&& HttpMethod.GET.equals(
						((RequestDataContext) completionContext.getLoadBalancerRequest().getContext()).method())));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ServerWebExchange mockExchange(ServiceInstance serviceInstance, LoadBalancerLifecycle lifecycleProcessor,
			boolean shouldThrowException) {
		Response response;
		when(lifecycleProcessor.supports(any(Class.class), any(Class.class), any(Class.class))).thenReturn(true);
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();
		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange serverWebExchange = MockServerWebExchange.from(request);
		if (serviceInstance == null) {
			response = new EmptyResponse();
		}
		else {
			response = new DefaultResponse(serviceInstance);
		}
		serverWebExchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, lbUri);
		serverWebExchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, new LinkedHashSet<>());
		serverWebExchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
		RoundRobinLoadBalancer loadBalancer = mock(RoundRobinLoadBalancer.class);
		when(loadBalancer.choose(any(Request.class))).thenReturn(Mono.just(response));
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);
		Map<String, LoadBalancerLifecycle> lifecycleProcessors = new HashMap<>();
		lifecycleProcessors.put("service1", lifecycleProcessor);
		when(clientFactory.getInstances("service1", LoadBalancerLifecycle.class)).thenReturn(lifecycleProcessors);
		if (shouldThrowException) {
			when(chain.filter(any())).thenReturn(Mono.error(new UnsupportedOperationException()));
		}
		else {
			when(chain.filter(any())).thenReturn(Mono.empty());
		}
		return serverWebExchange;
	}

	@NotNull
	private Map<String, String> buildHints(String hint) {
		Map<String, String> hints = new HashMap<>();
		hints.put("default", hint);
		return hints;
	}

	private ServerWebExchange testFilter(MockServerHttpRequest request, URI uri) {
		return testFilter(MockServerWebExchange.from(request), uri);
	}

	private ServerWebExchange testFilter(ServerWebExchange exchange, URI uri) {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(
				ServiceInstanceListSuppliers.toProvider("service1",
						new DefaultServiceInstance("service1_1", "service1", "service1-host1", 8081, false)),
				"service1", -1);
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);

		ReactiveLoadBalancerClientFilter filter = new ReactiveLoadBalancerClientFilter(clientFactory, properties,
				loadBalancerProperties);
		filter.filter(exchange, chain).block();

		return captor.getValue();
	}

}
