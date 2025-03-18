/*
 * Copyright 2013-2023 the original author or authors.
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

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycleValidator;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.ResponseData;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.getApplicationContext;

public abstract class LoadBalancerFilterFunctions {

	private static final Log log = LogFactory.getLog(LoadBalancerFilterFunctions.class);

	private LoadBalancerFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> lb(String serviceId) {
		return lb(serviceId, LoadBalancerUriTools::reconstructURI);
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> lb(String serviceId,
			BiFunction<ServiceInstance, URI, URI> reconstructUriFunction) {
		return (request, next) -> {
			MvcUtils.addOriginalRequestUrl(request, request.uri());

			LoadBalancerClientFactory clientFactory = getApplicationContext(request)
				.getBean(LoadBalancerClientFactory.class);
			Set<LoadBalancerLifecycle> supportedLifecycleProcessors = LoadBalancerLifecycleValidator
				.getSupportedLifecycleProcessors(clientFactory.getInstances(serviceId, LoadBalancerLifecycle.class),
						RequestDataContext.class, ResponseData.class, ServiceInstance.class);
			RequestData requestData = new RequestData(request.method(), request.uri(),
					request.headers().asHttpHeaders(), buildCookies(request.cookies()), request.attributes());
			DefaultRequest<RequestDataContext> lbRequest = new DefaultRequest<>(
					new RequestDataContext(requestData, getHint(clientFactory, serviceId)));

			LoadBalancerClient loadBalancerClient = clientFactory.getInstance(serviceId, LoadBalancerClient.class);
			if (loadBalancerClient == null) {
				throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE,
						"No loadbalancer available for " + serviceId);
			}
			supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStart(lbRequest));
			ServiceInstance retrievedInstance = loadBalancerClient.choose(serviceId, lbRequest);
			if (retrievedInstance == null) {
				supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
					.onComplete(new CompletionContext<>(CompletionContext.Status.DISCARD, lbRequest)));
				throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE,
						"Unable to find instance for " + serviceId);
				// throw NotFoundException.create(properties.isUse404(), "Unable to find
				// instance for " + serviceId);
			}

			URI uri = request.uri();

			// if the `lb:<scheme>` mechanism was used, use `<scheme>` as the default,
			// if the loadbalancer doesn't provide one.
			String scheme = retrievedInstance.isSecure() ? "https" : "http";

			DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(retrievedInstance, scheme);

			URI requestUrl = reconstructUriFunction.apply(serviceInstance, uri);

			if (log.isTraceEnabled()) {
				log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
			}
			MvcUtils.setRequestUrl(request, requestUrl);
			// exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
			DefaultResponse defaultResponse = new DefaultResponse(serviceInstance);

			supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStartRequest(lbRequest, defaultResponse));

			try {
				ServerResponse serverResponse = next.handle(request);
				supportedLifecycleProcessors
					.forEach(lifecycle -> lifecycle.onComplete(new CompletionContext<>(CompletionContext.Status.SUCCESS,
							lbRequest, defaultResponse, serverResponse)));
				return serverResponse;
			}
			catch (Exception e) {
				supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onComplete(
						new CompletionContext<>(CompletionContext.Status.FAILED, e, lbRequest, defaultResponse)));

				throw new RuntimeException(e);
			}
		};
	}

	private static String getHint(LoadBalancerClientFactory clientFactory, String serviceId) {
		LoadBalancerProperties loadBalancerProperties = clientFactory.getProperties(serviceId);
		Map<String, String> hints = loadBalancerProperties.getHint();
		String defaultHint = hints.getOrDefault("default", "default");
		String hintPropertyValue = hints.get(serviceId);
		return hintPropertyValue != null ? hintPropertyValue : defaultHint;
	}

	private static MultiValueMap<String, String> buildCookies(MultiValueMap<String, Cookie> cookies) {
		HttpHeaders newCookies = new HttpHeaders();
		if (cookies != null) {
			cookies.forEach((key, value) -> value
				.forEach(cookie -> newCookies.put(cookie.getName(), Collections.singletonList(cookie.getValue()))));
		}
		return newCookies;
	}

	static class DelegatingServiceInstance implements ServiceInstance {

		final ServiceInstance delegate;

		private String overrideScheme;

		DelegatingServiceInstance(ServiceInstance delegate, String overrideScheme) {
			this.delegate = delegate;
			this.overrideScheme = overrideScheme;
		}

		@Override
		public String getServiceId() {
			return delegate.getServiceId();
		}

		@Override
		public String getHost() {
			return delegate.getHost();
		}

		@Override
		public int getPort() {
			return delegate.getPort();
		}

		@Override
		public boolean isSecure() {
			// TODO: move to map
			if ("https".equals(this.overrideScheme) || "wss".equals(this.overrideScheme)) {
				return true;
			}
			return delegate.isSecure();
		}

		@Override
		public URI getUri() {
			return delegate.getUri();
		}

		@Override
		public Map<String, String> getMetadata() {
			return delegate.getMetadata();
		}

		@Override
		public String getScheme() {
			String scheme = delegate.getScheme();
			if (scheme != null) {
				return scheme;
			}
			return this.overrideScheme;
		}

	}

}
