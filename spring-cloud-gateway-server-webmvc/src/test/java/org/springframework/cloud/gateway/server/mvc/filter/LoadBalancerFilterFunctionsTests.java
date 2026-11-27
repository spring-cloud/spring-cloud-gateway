/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the WebMVC load balancer filter's missing-instance response.
 *
 * @author joecqupt
 */
class LoadBalancerFilterFunctionsTests {

	private final List<HttpMessageConverter<?>> converters = StreamSupport
		.stream(HttpMessageConverters.forServer().registerDefaults().build().spliterator(), false)
		.toList();

	@Test
	void returns503WhenNoInstanceByDefault() {
		ServerRequest request = request(new GatewayLoadBalancerProperties());

		assertThatThrownBy(() -> LoadBalancerFilterFunctions.lb("orders").filter(request, ignored -> null))
			.isInstanceOf(HttpServerErrorException.class)
			.satisfies(error -> assertThat(((HttpServerErrorException) error).getStatusCode())
				.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	@Test
	void returns404WhenNoInstanceAndUse404Enabled() {
		GatewayLoadBalancerProperties properties = new GatewayLoadBalancerProperties();
		properties.setUse404(true);
		ServerRequest request = request(properties);

		assertThatThrownBy(() -> LoadBalancerFilterFunctions.lb("orders").filter(request, ignored -> null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void bindsUse404Property() {
		new ApplicationContextRunner()
			.withUserConfiguration(FilterAutoConfiguration.LoadBalancerHandlerConfiguration.class)
			.withPropertyValues("spring.cloud.gateway.server.webmvc.loadbalancer.use404=true")
			.run(context -> assertThat(context.getBean(GatewayLoadBalancerProperties.class).isUse404()).isTrue());
	}

	private ServerRequest request(GatewayLoadBalancerProperties properties) {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("/orders")
			.buildRequest(new MockServletContext());
		WebApplicationContext applicationContext = mock(WebApplicationContext.class);
		LoadBalancerClientFactory clientFactory = mock(LoadBalancerClientFactory.class);
		LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);
		servletRequest.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
		when(applicationContext.getBean(LoadBalancerClientFactory.class)).thenReturn(clientFactory);
		when(applicationContext.getBean(GatewayLoadBalancerProperties.class)).thenReturn(properties);
		when(clientFactory.getInstances("orders", LoadBalancerLifecycle.class)).thenReturn(Collections.emptyMap());
		when(clientFactory.getProperties("orders")).thenReturn(new LoadBalancerProperties());
		when(clientFactory.getInstance("orders", LoadBalancerClient.class)).thenReturn(loadBalancerClient);
		return ServerRequest.create(servletRequest, converters);
	}

}
