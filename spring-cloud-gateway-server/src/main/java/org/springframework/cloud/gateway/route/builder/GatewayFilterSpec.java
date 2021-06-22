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

package org.springframework.cloud.gateway.route.builder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.retry.Repeat;
import reactor.retry.Retry;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractChangeRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory.Strategy;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory.StripVersion;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SaveSessionGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;

/**
 * Applies specific filters to routes.
 */
public class GatewayFilterSpec extends UriSpec {

	private static final Log log = LogFactory.getLog(GatewayFilterSpec.class);

	public GatewayFilterSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	/**
	 * Applies the filter to the route.
	 * @param gatewayFilter the filter to apply
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec filter(GatewayFilter gatewayFilter) {
		if (gatewayFilter instanceof Ordered) {
			this.routeBuilder.filter(gatewayFilter);
			return this;
		}
		return this.filter(gatewayFilter, 0);
	}

	/**
	 * Applies the filter to the route.
	 * @param gatewayFilter the filter to apply
	 * @param order the order to apply the filter
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec filter(GatewayFilter gatewayFilter, int order) {
		if (gatewayFilter instanceof Ordered) {
			this.routeBuilder.filter(gatewayFilter);
			log.warn("GatewayFilter already implements ordered " + gatewayFilter.getClass()
					+ "ignoring order parameter: " + order);
			return this;
		}
		this.routeBuilder.filter(new OrderedGatewayFilter(gatewayFilter, order));
		return this;
	}

	/**
	 * Applies the list of filters to the route.
	 * @param gatewayFilters the filters to apply
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec filters(GatewayFilter... gatewayFilters) {
		List<GatewayFilter> filters = transformToOrderedFilters(Stream.of(gatewayFilters));
		this.routeBuilder.filters(filters);
		return this;
	}

	public List<GatewayFilter> transformToOrderedFilters(Stream<GatewayFilter> stream) {
		return stream.map(filter -> {
			if (filter instanceof Ordered) {
				return filter;
			}
			else {
				return new OrderedGatewayFilter(filter, 0);
			}
		}).collect(Collectors.toList());
	}

	/**
	 * Applies the list of filters to the route.
	 * @param gatewayFilters the filters to apply
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec filters(Collection<GatewayFilter> gatewayFilters) {
		List<GatewayFilter> filters = transformToOrderedFilters(gatewayFilters.stream());
		this.routeBuilder.filters(filters);
		return this;
	}

	/**
	 * Adds a request header to the request before it is routed by the Gateway.
	 * @param headerName the header name
	 * @param headerValue the header value
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec addRequestHeader(String headerName, String headerValue) {
		return filter(getBean(AddRequestHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * Adds a request parameter to the request before it is routed by the Gateway.
	 * @param param the parameter name
	 * @param value the parameter vaule
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec addRequestParameter(String param, String value) {
		return filter(
				getBean(AddRequestParameterGatewayFilterFactory.class).apply(c -> c.setName(param).setValue(value)));
	}

	/**
	 * Adds a header to the response returned to the Gateway from the route.
	 * @param headerName the header name
	 * @param headerValue the header value
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec addResponseHeader(String headerName, String headerValue) {
		return filter(getBean(AddResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * A filter that removes duplication on a response header before it is returned to the
	 * client by the Gateway.
	 * @param headerName the header name(s), space separated
	 * @param strategy RETAIN_FIRST, RETAIN_LAST, or RETAIN_UNIQUE
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec dedupeResponseHeader(String headerName, String strategy) {
		return filter(getBean(DedupeResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setStrategy(Strategy.valueOf(strategy)).setName(headerName)));
	}

	public GatewayFilterSpec circuitBreaker(Consumer<SpringCloudCircuitBreakerFilterFactory.Config> configConsumer) {
		SpringCloudCircuitBreakerFilterFactory filterFactory;
		try {
			filterFactory = getBean(SpringCloudCircuitBreakerFilterFactory.class);
		}
		catch (NoSuchBeanDefinitionException e) {
			throw new NoSuchBeanDefinitionException(SpringCloudCircuitBreakerFilterFactory.class,
					"There needs to be a circuit breaker implementation on the classpath that supports reactive APIs.");
		}
		return filter(filterFactory.apply(this.routeBuilder.getId(), configConsumer));
	}

	/**
	 * Maps headers from one name to another.
	 * @param fromHeader the header name of the original header.
	 * @param toHeader the header name of the new header.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec mapRequestHeader(String fromHeader, String toHeader) {
		return filter(getBean(MapRequestHeaderGatewayFilterFactory.class)
				.apply(c -> c.setFromHeader(fromHeader).setToHeader(toHeader)));
	}

	/**
	 * A filter that can be used to modify the request body.
	 * @param inClass the class to convert the incoming request body to
	 * @param outClass the class the Gateway will add to the request before it is routed
	 * @param rewriteFunction the {@link RewriteFunction} that transforms the request body
	 * @param <T> the original request body class
	 * @param <R> the new request body class
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	// TODO: setup custom spec
	public <T, R> GatewayFilterSpec modifyRequestBody(Class<T> inClass, Class<R> outClass,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyRequestBodyGatewayFilterFactory.class)
				.apply(c -> c.setRewriteFunction(inClass, outClass, rewriteFunction)));
	}

	/**
	 * A filter that can be used to modify the request body.
	 * @param inClass the class to convert the incoming request body to
	 * @param outClass the class the Gateway will add to the request before it is routed
	 * @param newContentType the new Content-Type header to be sent
	 * @param rewriteFunction the {@link RewriteFunction} that transforms the request body
	 * @param <T> the original request body class
	 * @param <R> the new request body class
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public <T, R> GatewayFilterSpec modifyRequestBody(Class<T> inClass, Class<R> outClass, String newContentType,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyRequestBodyGatewayFilterFactory.class)
				.apply(c -> c.setRewriteFunction(inClass, outClass, rewriteFunction).setContentType(newContentType)));
	}

	/**
	 * A filter that can be used to modify the request body.
	 * @param configConsumer request spec for response modification
	 * @param <T> the original request body class
	 * @param <R> the new request body class
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 * <pre>
	 * {@code
	 * ...
	 * .modifyRequestBody(c -> c
	 *		.setInClass(Some.class)
	 *		.setOutClass(SomeOther.class)
	 *		.setInHints(hintsIn)
	 *		.setOutHints(hintsOut)
	 *		.setRewriteFunction(rewriteFunction))
	 * }
	 * </pre>
	 */
	public <T, R> GatewayFilterSpec modifyRequestBody(
			Consumer<ModifyRequestBodyGatewayFilterFactory.Config> configConsumer) {
		return filter(getBean(ModifyRequestBodyGatewayFilterFactory.class).apply(configConsumer));
	}

	/**
	 * A filter that can be used to modify the response body.
	 * @param inClass the class to conver the response body to
	 * @param outClass the class the Gateway will add to the response before it is
	 * returned to the client
	 * @param rewriteFunction the {@link RewriteFunction} that transforms the response
	 * body
	 * @param <T> the original response body class
	 * @param <R> the new response body class
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public <T, R> GatewayFilterSpec modifyResponseBody(Class<T> inClass, Class<R> outClass,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyResponseBodyGatewayFilterFactory.class)
				.apply(c -> c.setRewriteFunction(inClass, outClass, rewriteFunction)));
	}

	/**
	 * A filter that can be used to modify the response body.
	 * @param inClass the class to conver the response body to
	 * @param outClass the class the Gateway will add to the response before it is
	 * returned to the client
	 * @param newContentType the new Content-Type header to be returned
	 * @param rewriteFunction the {@link RewriteFunction} that transforms the response
	 * body
	 * @param <T> the original response body class
	 * @param <R> the new response body class
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	// TODO: setup custom spec
	public <T, R> GatewayFilterSpec modifyResponseBody(Class<T> inClass, Class<R> outClass, String newContentType,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyResponseBodyGatewayFilterFactory.class).apply(
				c -> c.setRewriteFunction(inClass, outClass, rewriteFunction).setNewContentType(newContentType)));
	}

	/**
	 * A filter that can be used to modify the response body using custom spec.
	 * @param configConsumer response spec for response modification
	 * @param <T> the original response body class
	 * @param <R> the new response body class
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 * <pre>
	 * {@code
	 * ...
	 * .modifyResponseBody(c -> c
	 *		.setInClass(Some.class)
	 *		.setOutClass(SomeOther.class)
	 *		.setOutHints(hintsOut)
	 *		.setRewriteFunction(rewriteFunction))
	 * }
	 * </pre>
	 */
	public <T, R> GatewayFilterSpec modifyResponseBody(
			Consumer<ModifyResponseBodyGatewayFilterFactory.Config> configConsumer) {
		return filter(getBean(ModifyResponseBodyGatewayFilterFactory.class).apply(configConsumer));
	}

	/**
	 * A filter that can be used to add a prefix to the path of a request before it is
	 * routed by the Gateway.
	 * @param prefix the prefix to add to the path
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec prefixPath(String prefix) {
		return filter(getBean(PrefixPathGatewayFilterFactory.class).apply(c -> c.setPrefix(prefix)));
	}

	/**
	 * A filter that will preserve the host header of the request on the outgoing request
	 * from the Gateway.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec preserveHostHeader() {
		return filter(getBean(PreserveHostHeaderGatewayFilterFactory.class).apply());
	}

	/**
	 * A filter that will set the Host header to
	 * {@param hostName} on the outgoing
	 * request.
	 * @param hostName the updated Host header value
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setHostHeader(String hostName) {
		return filter(getBean(SetRequestHostHeaderGatewayFilterFactory.class).apply(c -> c.setHost(hostName)));
	}

	/**
	 * A filter that will return a redirect response back to the client.
	 * @param status an HTTP status code, should be a {@code 300} series redirect
	 * @param url the URL to redirect to. This URL will be set in the {@code location}
	 * header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec redirect(int status, URI url) {
		return redirect(String.valueOf(status), url.toString());
	}

	/**
	 * A filter that will return a redirect response back to the client.
	 * @param status an HTTP status code, should be a {@code 300} series redirect
	 * @param url the URL to redirect to. This URL will be set in the {@code location}
	 * header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec redirect(int status, String url) {
		return redirect(String.valueOf(status), url);
	}

	/**
	 * A filter that will return a redirect response back to the client.
	 * @param status an HTTP status code, should be a {@code 300} series redirect
	 * @param url the URL to redirect to. This URL will be set in the {@code location}
	 * header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec redirect(String status, URI url) {
		return redirect(status, url);
	}

	/**
	 * A filter that will return a redirect response back to the client.
	 * @param status an HTTP status code, should be a {@code 300} series redirect
	 * @param url the URL to redirect to. This URL will be set in the {@code location}
	 * header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec redirect(String status, String url) {
		return filter(getBean(RedirectToGatewayFilterFactory.class).apply(status, url));
	}

	/**
	 * A filter that will return a redirect response back to the client.
	 * @param status an HTTP status code, should be a {@code 300} series redirect
	 * @param url the URL to redirect to. This URL will be set in the {@code location}
	 * header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec redirect(HttpStatus status, URL url) {
		try {
			return filter(getBean(RedirectToGatewayFilterFactory.class).apply(status, url.toURI()));
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid URL", e);
		}
	}

	/**
	 * A filter that will remove a request header before the request is routed by the
	 * Gateway.
	 * @param headerName the name of the header to remove
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec removeRequestHeader(String headerName) {
		return filter(getBean(RemoveRequestHeaderGatewayFilterFactory.class).apply(c -> c.setName(headerName)));
	}

	/**
	 * A filter that will remove a request param before the request is routed by the
	 * Gateway.
	 * @param paramName the name of the header to remove
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec removeRequestParameter(String paramName) {
		return filter(getBean(RemoveRequestParameterGatewayFilterFactory.class).apply(c -> c.setName(paramName)));
	}

	/**
	 * A filter that will remove a response header before the Gateway returns the response
	 * to the client.
	 * @param headerName the name of the header to remove
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec removeResponseHeader(String headerName) {
		return filter(getBean(RemoveResponseHeaderGatewayFilterFactory.class).apply(c -> c.setName(headerName)));
	}

	/**
	 * A filter that will set up a request rate limiter for a route.
	 * @param configConsumer a {@link Consumer} that will return configuration for the
	 * rate limiter
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec requestRateLimiter(
			Consumer<RequestRateLimiterGatewayFilterFactory.Config> configConsumer) {
		return filter(
				getBean(RequestRateLimiterGatewayFilterFactory.class).apply(this.routeBuilder.getId(), configConsumer));
	}

	/**
	 * A filter that will set up a request rate limiter for a route.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public RequestRateLimiterSpec requestRateLimiter() {
		return new RequestRateLimiterSpec(getBean(RequestRateLimiterGatewayFilterFactory.class));
	}

	/**
	 * A filter which rewrites the request path before it is routed by the Gateway.
	 * @param regex a Java regular expression to match the path against
	 * @param replacement the replacement for the path
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec rewritePath(String regex, String replacement) {
		return filter(getBean(RewritePathGatewayFilterFactory.class)
				.apply(c -> c.setRegexp(regex).setReplacement(replacement)));
	}

	/**
	 * A filter that will retry failed requests. By default {@code 5xx} errors and
	 * {@code GET}s are retryable.
	 * @param retries max number of retries
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec retry(int retries) {
		return filter(getBean(RetryGatewayFilterFactory.class).apply(this.routeBuilder.getId(),
				retryConfig -> retryConfig.setRetries(retries)));
	}

	/**
	 * A filter that will retry failed requests.
	 * @param retryConsumer a {@link Consumer} which returns a
	 * {@link org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory.RetryConfig}
	 * to configure the retry functionality
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec retry(Consumer<RetryGatewayFilterFactory.RetryConfig> retryConsumer) {
		return filter(getBean(RetryGatewayFilterFactory.class).apply(this.routeBuilder.getId(), retryConsumer));
	}

	/**
	 * A filter that will retry failed requests.
	 * @param repeat a {@link Repeat}
	 * @param retry a {@link Retry}
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec retry(Repeat<ServerWebExchange> repeat, Retry<ServerWebExchange> retry) {
		RetryGatewayFilterFactory filterFactory = getBean(RetryGatewayFilterFactory.class);
		return filter(filterFactory.apply(this.routeBuilder.getId(), repeat, retry));
	}

	/**
	 * A filter that adds a number of headers to the response at the reccomendation from
	 * <a href="https://blog.appcanary.com/2017/http-security-headers.html">this blog
	 * post</a>.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec secureHeaders() {
		return filter(getBean(SecureHeadersGatewayFilterFactory.class).apply(config -> {
		}));
	}

	/**
	 * A filter that adds a number of headers to the response at the reccomendation from
	 * <a href="https://blog.appcanary.com/2017/http-security-headers.html">this blog
	 * post</a>.
	 * @param configConsumer self define headers
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec secureHeaders(Consumer<SecureHeadersGatewayFilterFactory.Config> configConsumer) {
		return filter(getBean(SecureHeadersGatewayFilterFactory.class).apply(configConsumer));
	}

	/**
	 * A filter that sets the path of the request before it is routed by the Gateway.
	 * @param template the path to set on the request, allows multiple matching segments
	 * using URI templates from Spring Framework
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setPath(String template) {
		return filter(getBean(SetPathGatewayFilterFactory.class).apply(c -> c.setTemplate(template)));
	}

	/**
	 * A filter that sets a header on the request before it is routed by the Gateway.
	 * @param headerName the header name
	 * @param headerValue the value of the header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setRequestHeader(String headerName, String headerValue) {
		return filter(getBean(SetRequestHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * A filter that sets a header on the response before it is returned to the client by
	 * the Gateway.
	 * @param headerName the header name
	 * @param headerValue the value of the header
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setResponseHeader(String headerName, String headerValue) {
		return filter(getBean(SetResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * A filter that rewrites a header value on the response before it is returned to the
	 * client by the Gateway.
	 * @param headerName the header name
	 * @param regex a Java regular expression to match the path against
	 * @param replacement the replacement for the path
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec rewriteResponseHeader(String headerName, String regex, String replacement) {
		return filter(getBean(RewriteResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setReplacement(replacement).setRegexp(regex).setName(headerName)));
	}

	/**
	 * A filter that rewrites the value of Location response header, ridding it of backend
	 * specific details.
	 * @param stripVersionMode NEVER_STRIP, AS_IN_REQUEST, or ALWAYS_STRIP
	 * @param locationHeaderName a location header name
	 * @param hostValue host value
	 * @param protocolsRegex a valid regex String, against which the protocol name will be
	 * matched
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec rewriteLocationResponseHeader(String stripVersionMode, String locationHeaderName,
			String hostValue, String protocolsRegex) {
		return filter(getBean(RewriteLocationResponseHeaderGatewayFilterFactory.class).apply(
				c -> c.setStripVersion(StripVersion.valueOf(stripVersionMode)).setLocationHeaderName(locationHeaderName)
						.setHostValue(hostValue).setProtocols(protocolsRegex)));
	}

	/**
	 * A filter that sets the status on the response before it is returned to the client
	 * by the Gateway.
	 * @param status the status to set on the response
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setStatus(int status) {
		return setStatus(String.valueOf(status));
	}

	/**
	 * A filter that sets the status on the response before it is returned to the client
	 * by the Gateway.
	 * @param status the status to set on the response
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setStatus(HttpStatus status) {
		return setStatus(status.name());
	}

	/**
	 * A filter that sets the status on the response before it is returned to the client
	 * by the Gateway.
	 * @param status the status to set on the response
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setStatus(String status) {
		return filter(getBean(SetStatusGatewayFilterFactory.class).apply(c -> c.setStatus(status)));
	}

	/**
	 * A filter which forces a {@code WebSession::save} operation before forwarding the
	 * call downstream. This is of particular use when using something like
	 * <a href="https://projects.spring.io/spring-session/">Spring Session</a> with a lazy
	 * data store and need to ensure the session state has been saved before making the
	 * forwarded call. If you are integrating
	 * <a href="https://projects.spring.io/spring-security/">Spring Security</a> with
	 * Spring Session, and want to ensure security details have been forwarded to the
	 * remote process, this is critical.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	@SuppressWarnings("unchecked")
	public GatewayFilterSpec saveSession() {
		return filter(getBean(SaveSessionGatewayFilterFactory.class).apply(c -> {
		}));
	}

	/**
	 * Strips the prefix from the path of the request before it is routed by the Gateway.
	 * @param parts the number of parts of the path to remove
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec stripPrefix(int parts) {
		return filter(getBean(StripPrefixGatewayFilterFactory.class).apply(c -> c.setParts(parts)));
	}

	/**
	 * A filter which changes the URI the request will be routed to by the Gateway by
	 * pulling it from a header on the request.
	 * @param headerName the header name containing the URI
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec requestHeaderToRequestUri(String headerName) {
		return filter(getBean(RequestHeaderToRequestUriGatewayFilterFactory.class).apply(c -> c.setName(headerName)));
	}

	/**
	 * A filter which change the URI the request will be routed to by the Gateway.
	 * @param determineRequestUri a {@link Function} which takes a
	 * {@link ServerWebExchange} and returns a URI to route the request to
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec changeRequestUri(Function<ServerWebExchange, Optional<URI>> determineRequestUri) {
		return filter(new AbstractChangeRequestUriGatewayFilterFactory<Object>(Object.class) {
			@Override
			protected Optional<URI> determineRequestUri(ServerWebExchange exchange, Object config) {
				return determineRequestUri.apply(exchange);
			}
		}.apply(c -> {
		}));
	}

	/**
	 * A filter that sets the maximum permissible size of a Request.
	 * @param size the maximum size of a request
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setRequestSize(Long size) {
		return setRequestSize(DataSize.ofBytes(size));
	}

	/**
	 * A filter that sets the maximum permissible size of a Request.
	 * @param size the maximum size of a request
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setRequestSize(DataSize size) {
		return filter(getBean(RequestSizeGatewayFilterFactory.class).apply(c -> c.setMaxSize(size)));
	}

	/**
	 * A filter that sets the maximum permissible size of headers of Request.
	 * @param size the maximum size of header of request
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec setRequestHeaderSize(DataSize size) {
		return filter(getBean(RequestHeaderSizeGatewayFilterFactory.class).apply(c -> c.setMaxSize(size)));
	}

	/**
	 * A filter that enables token relay.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec tokenRelay() {
		try {
			return filter(getBean(TokenRelayGatewayFilterFactory.class).apply(o -> {
			}));
		}
		catch (NoSuchBeanDefinitionException e) {
			throw new IllegalStateException("No TokenRelayGatewayFilterFactory bean was found. Did you include the "
					+ "org.springframework.boot:spring-boot-starter-oauth2-client dependency?");
		}
	}

	/**
	 * Adds hystrix execution exception headers to fallback request. Depends on @{code
	 * org.springframework.cloud::spring-cloud-starter-netflix-hystrix} being on the
	 * classpath, {@see https://cloud.spring.io/spring-cloud-netflix/}
	 * @param config a {@link FallbackHeadersGatewayFilterFactory.Config} which provides
	 * the header names. If header names arguments are not provided, default values are
	 * used.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec fallbackHeaders(FallbackHeadersGatewayFilterFactory.Config config) {
		FallbackHeadersGatewayFilterFactory factory = getFallbackHeadersGatewayFilterFactory();
		return filter(factory.apply(config));
	}

	/**
	 * Adds hystrix execution exception headers to fallback request. Depends on @{code
	 * org.springframework.cloud::spring-cloud-starter-netflix-hystrix} being on the
	 * classpath, {@see https://cloud.spring.io/spring-cloud-netflix/}
	 * @param configConsumer a {@link Consumer} which can be used to set up the names of
	 * the headers in the config. If header names arguments are not provided, default
	 * values are used.
	 * @return a {@link GatewayFilterSpec} that can be used to apply additional filters
	 */
	public GatewayFilterSpec fallbackHeaders(Consumer<FallbackHeadersGatewayFilterFactory.Config> configConsumer) {
		FallbackHeadersGatewayFilterFactory factory = getFallbackHeadersGatewayFilterFactory();
		return filter(factory.apply(configConsumer));
	}

	private FallbackHeadersGatewayFilterFactory getFallbackHeadersGatewayFilterFactory() {
		FallbackHeadersGatewayFilterFactory factory;
		try {
			factory = getBean(FallbackHeadersGatewayFilterFactory.class);
		}
		catch (NoSuchBeanDefinitionException e) {
			throw new NoSuchBeanDefinitionException(FallbackHeadersGatewayFilterFactory.class,
					"This is probably because Hystrix is missing from the classpath, which can be resolved by adding dependency on 'org.springframework.cloud:spring-cloud-starter-netflix-hystrix'");
		}
		return factory;
	}

	public class RequestRateLimiterSpec {

		private final RequestRateLimiterGatewayFilterFactory filter;

		public RequestRateLimiterSpec(RequestRateLimiterGatewayFilterFactory filter) {
			this.filter = filter;
		}

		public <C, R extends RateLimiter<C>> RequestRateLimiterSpec rateLimiter(Class<R> rateLimiterType,
				Consumer<C> configConsumer) {
			R rateLimiter = getBean(rateLimiterType);
			C config = rateLimiter.newConfig();
			configConsumer.accept(config);
			rateLimiter.getConfig().put(routeBuilder.getId(), config);
			return this;
		}

		public GatewayFilterSpec configure(Consumer<RequestRateLimiterGatewayFilterFactory.Config> configConsumer) {
			filter(this.filter.apply(routeBuilder.getId(), configConsumer));
			return GatewayFilterSpec.this;
		}

		// useful when nothing to configure
		public GatewayFilterSpec and() {
			return configure(config -> {
			});
		}

	}

}
