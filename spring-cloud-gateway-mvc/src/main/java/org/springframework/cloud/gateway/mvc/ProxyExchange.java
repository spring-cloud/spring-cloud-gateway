/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.gateway.mvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.function.Function;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

/**
 * A <code>@RequestMapping</code> argument type that can proxy the request to a backend.
 * Spring will inject one of these into your MVC handler method, and you get return a
 * <code>ResponseEntity</code> that you get from one of the HTTP methods {@link #get()},
 * {@link #post()}, {@link #put()}, {@link #patch()}, {@link #delete()} etc. Example:
 * 
 * <pre>
 * &#64;GetMapping("/proxy/{id}")
 * public ResponseEntity&lt;?&gt; proxy(@PathVariable Integer id, ProxyExchange&lt;?&gt; proxy)
 * 		throws Exception {
 * 	return proxy.uri("http://localhost:9000/foos/" + id).get();
 * }
 * </pre>
 * 
 * <p>
 * By default the incoming request body and headers are sent intact to the downstream
 * service (with the exception of "sensitive" headers). To manipulate the downstream
 * request there are "builder" style methods in {@link ProxyExchange}, but only the
 * {@link #uri(String)} is mandatory. You can change the sensitive headers by calling the
 * {@link #sensitive(String...)} method (Authorization and Cookie are sensitive by
 * default).
 * </p>
 * <p>
 * The type parameter <code>T</code> in <code>ProxyExchange&lt;T&gt;</code> is the type of
 * the response body, so it comes out in the {@link ResponseEntity} that you return from
 * your <code>@RequestMapping</code>. If you don't care about the type of the request and
 * response body (e.g. if it's just a passthru) then use a wildcard, or
 * <code>byte[]</code> or <code>Object</code>. Use a concrete type if you want to
 * transform or manipulate the response, or if you want to assert that it is convertible
 * to the type you declare.
 * </p>
 * <p>
 * To manipulate the response use the overloaded HTTP methods with a <code>Function</code>
 * argument and pass in code to transform the response. E.g.
 * 
 * <pre>
 * &#64;PostMapping("/proxy")
 * public ResponseEntity&lt;Foo&gt; proxy(ProxyExchange&lt;Foo&gt; proxy) throws Exception {
 * 	return proxy.uri("http://localhost:9000/foos/") //
 * 			.post(response -> ResponseEntity.status(response.getStatusCode()) //
 * 					.headers(response.getHeaders()) //
 * 					.header("X-Custom", "MyCustomHeader") //
 * 					.body(response.getBody()) //
 * 			);
 * }
 * 
 * </pre>
 * 
 * </p>
 * <p>
 * The full machinery of Spring {@link HttpMessageConverter message converters} is applied
 * to the incoming request and response and also to the backend request. If you need
 * additional converters then they need to be added upstream in the MVC configuration and
 * also to the {@link RestTemplate} that is used in the backend calls (see the
 * {@link ProxyExchange#ProxyExchange(RestTemplate, NativeWebRequest, ModelAndViewContainer, WebDataBinderFactory, Type)
 * constructor} for details).
 * </p>
 * <p>
 * As well as the HTTP methods for a backend call you can also use
 * {@link #forward(String)} for a local in-container dispatch.
 * </p>
 * 
 * @author Dave Syer
 *
 */
public class ProxyExchange<T> {

	public static Set<String> DEFAULT_SENSITIVE = new HashSet<>(
			Arrays.asList("cookie", "authorization"));

	private URI uri;

	private RestTemplate rest;

	private Object body;

	private RequestResponseBodyMethodProcessor delegate;

	private NativeWebRequest webRequest;

	private ModelAndViewContainer mavContainer;

	private WebDataBinderFactory binderFactory;

	private Set<String> sensitive;

	private HttpHeaders headers = new HttpHeaders();

	private Type responseType;

	public ProxyExchange(RestTemplate rest, NativeWebRequest webRequest,
			ModelAndViewContainer mavContainer, WebDataBinderFactory binderFactory,
			Type type) {
		this.responseType = type;
		this.rest = rest;
		this.webRequest = webRequest;
		this.mavContainer = mavContainer;
		this.binderFactory = binderFactory;
		this.delegate = new RequestResponseBodyMethodProcessor(
				rest.getMessageConverters());
	}

	/**
	 * Sets the body for the downstream request (if using {@link #post()}, {@link #put()}
	 * or {@link #patch()}). The body can be omitted if you just want to pass the incoming
	 * request downstream without changing it. If you want to transform the incoming
	 * request you can declare it as a <code>@RequestBody</code> in your
	 * <code>@RequestMapping</code> in the usual Spring MVC way.
	 * 
	 * @param body the request body to send downstream
	 * @return this for convenience
	 */
	public ProxyExchange<T> body(Object body) {
		this.body = body;
		return this;
	}

	/**
	 * Sets a header for the downstream call.
	 * 
	 * @param name
	 * @param value
	 * @return this for convenience
	 */
	public ProxyExchange<T> header(String name, String... value) {
		this.headers.put(name, Arrays.asList(value));
		return this;
	}

	/**
	 * Additional headers, or overrides of the incoming ones, to be used in the downstream
	 * call.
	 * 
	 * @param headers the http headers to use in the downstream call
	 * @return this for convenience
	 */
	public ProxyExchange<T> headers(HttpHeaders headers) {
		this.headers.putAll(headers);
		return this;
	}

	/**
	 * Sets the names of sensitive headers that are not passed downstream to the backend
	 * service.
	 * 
	 * @param names the names of sensitive headers
	 * @return this for convenience
	 */
	public ProxyExchange<T> sensitive(String... names) {
		if (this.sensitive == null) {
			this.sensitive = new HashSet<>();
		}
		for (String name : names) {
			this.sensitive.add(name.toLowerCase());
		}
		return this;
	}

	/**
	 * Sets the uri for the backend call when triggered by the HTTP methods.
	 * 
	 * @param uri the backend uri to send the request to
	 * @return this for convenience
	 */
	public ProxyExchange<T> uri(String uri) {
		try {
			this.uri = new URI(uri);
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot create URI", e);
		}
		return this;
	}

	public String path() {
		return (String) this.webRequest.getAttribute(
				HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
				WebRequest.SCOPE_REQUEST);
	}

	public String path(String prefix) {
		String path = path();
		if (!path.startsWith(prefix)) {
			throw new IllegalArgumentException(
					"Path does not start with prefix (" + prefix + "): " + path);
		}
		return path.substring(prefix.length());
	}

	public void forward(String path) {
		HttpServletRequest request = this.webRequest
				.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse response = this.webRequest
				.getNativeResponse(HttpServletResponse.class);
		try {
			request.getRequestDispatcher(path).forward(
					new BodyForwardingHttpServletRequest(request, response), response);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot forward request", e);
		}
	}

	public ResponseEntity<T> get() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.get(uri))
				.build();
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> get(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(get());
	}

	public ResponseEntity<T> head() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.head(uri))
				.build();
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> head(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(head());
	}

	public ResponseEntity<T> options() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.options(uri))
				.build();
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> options(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(options());
	}

	public ResponseEntity<T> post() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.post(uri))
				.body(body());
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> post(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(post());
	}

	public ResponseEntity<T> delete() {
		RequestEntity<Void> requestEntity = headers(
				(BodyBuilder) RequestEntity.delete(uri)).build();
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> delete(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(delete());
	}

	public ResponseEntity<T> put() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.put(uri))
				.body(body());
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> put(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(put());
	}

	public ResponseEntity<T> patch() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.patch(uri))
				.body(body());
		return exchange(requestEntity);
	}

	public <S> ResponseEntity<S> patch(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(patch());
	}

	private ResponseEntity<T> exchange(RequestEntity<?> requestEntity) {
		Type type = this.responseType;
		if (type instanceof TypeVariable || type instanceof WildcardType) {
			type = Object.class;
		}
		return rest.exchange(requestEntity,
				ParameterizedTypeReference.forType(responseType));
	}

	private BodyBuilder headers(BodyBuilder builder) {
		Set<String> sensitive = this.sensitive;
		if (sensitive == null) {
			sensitive = DEFAULT_SENSITIVE;
		}
		proxy();
		for (String name : headers.keySet()) {
			if (sensitive.contains(name.toLowerCase())) {
				continue;
			}
			builder.header(name, headers.get(name).toArray(new String[0]));
		}
		return builder;
	}

	private void proxy() {
		try {
			URI uri = new URI(webRequest.getNativeRequest(HttpServletRequest.class)
					.getRequestURL().toString());
			appendForwarded(uri);
			appendXForwarded(uri);
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot create URI for request: " + webRequest
					.getNativeRequest(HttpServletRequest.class).getRequestURL());
		}
	}

	private void appendXForwarded(URI uri) {
		// Append the legacy headers if they were already added upstream
		String host = headers.getFirst("x-forwarded-host");
		if (host == null) {
			return;
		}
		host = host + "," + uri.getHost();
		headers.set("x-forwarded-host", host);
		String proto = headers.getFirst("x-forwarded-proto");
		if (proto == null) {
			return;
		}
		proto = proto + "," + uri.getScheme();
		headers.set("x-forwarded-proto", proto);
	}

	private void appendForwarded(URI uri) {
		String forwarded = headers.getFirst("forwarded");
		if (forwarded != null) {
			forwarded = forwarded + ",";
		}
		else {
			forwarded = "";
		}
		forwarded = forwarded + forwarded(uri);
		headers.set("forwarded", forwarded);
	}

	private String forwarded(URI uri) {
		if ("http".equals(uri.getScheme())) {
			return "host=" + uri.getHost();
		}
		return String.format("host=%s;proto=%s", uri.getHost(), uri.getScheme());
	}

	private Object body() {
		if (body != null) {
			return body;
		}
		body = getRequestBody();
		return body;
	}

	/**
	 * Search for the request body if it was already deserialized using
	 * <code>@RequestBody</code>. If it is not found then deserialize it in the same way
	 * that it would have been for a <code>@RequestBody</code>.
	 * 
	 * @return the request body
	 */
	private Object getRequestBody() {
		for (String key : mavContainer.getModel().keySet()) {
			if (key.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
				BindingResult result = (BindingResult) mavContainer.getModel().get(key);
				return result.getTarget();
			}
		}
		MethodParameter input = new MethodParameter(
				ClassUtils.getMethod(BodyGrabber.class, "body", Object.class), 0);
		try {
			delegate.resolveArgument(input, mavContainer, webRequest, binderFactory);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot resolve body", e);
		}
		String name = Conventions.getVariableNameForParameter(input);
		BindingResult result = (BindingResult) mavContainer.getModel()
				.get(BindingResult.MODEL_KEY_PREFIX + name);
		return result.getTarget();
	}

	/**
	 * A servlet request wrapper that can be safely passed downstream to an internal
	 * forward dispatch, caching its body, and making it available in converted form using
	 * Spring message converters.
	 *
	 */
	class BodyForwardingHttpServletRequest extends HttpServletRequestWrapper {
		private HttpServletRequest request;
		private HttpServletResponse response;

		BodyForwardingHttpServletRequest(HttpServletRequest request,
				HttpServletResponse response) {
			super(request);
			this.request = request;
			this.response = response;
		}

		private List<String> header(String name) {
			List<String> list = headers.get(name);
			return list;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			Object body = body();
			MethodParameter output = new MethodParameter(
					ClassUtils.getMethod(BodySender.class, "body"), -1);
			ServletOutputToInputConverter response = new ServletOutputToInputConverter(
					this.response);
			ServletWebRequest webRequest = new ServletWebRequest(this.request, response);
			try {
				delegate.handleReturnValue(body, output, mavContainer, webRequest);
			}
			catch (HttpMessageNotWritableException
					| HttpMediaTypeNotAcceptableException e) {
				throw new IllegalStateException("Cannot convert body", e);
			}
			return response.getInputStream();
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			Set<String> names = headers.keySet();
			if (names.isEmpty()) {
				return super.getHeaderNames();
			}
			Set<String> result = new LinkedHashSet<>(names);
			result.addAll(Collections.list(super.getHeaderNames()));
			return new Vector<String>(result).elements();
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			List<String> list = header(name);
			if (list != null) {
				return new Vector<String>(list).elements();
			}
			return super.getHeaders(name);
		}

		@Override
		public String getHeader(String name) {
			List<String> list = header(name);
			if (list != null && !list.isEmpty()) {
				return list.iterator().next();
			}
			return super.getHeader(name);
		}
	}

	protected static class BodyGrabber {
		public Object body(@RequestBody Object body) {
			return body;
		}
	}

	protected static class BodySender {
		@ResponseBody
		public Object body() {
			return null;
		}
	}

}

/**
 * Convenience class that converts an incoming request input stream into a form that can
 * be easily deserialized to a Java object using Spring message converters. It is only
 * used in a local forward dispatch, in which case there is a danger that the request body
 * will need to be read and analysed more than once. Apart from using the message
 * converters the other main feature of this class is that the request body is cached and
 * can be read repeatedly as necessary.
 * 
 * @author Dave Syer
 *
 */
class ServletOutputToInputConverter extends HttpServletResponseWrapper {

	private StringBuilder builder = new StringBuilder();

	public ServletOutputToInputConverter(HttpServletResponse response) {
		super(response);
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return new ServletOutputStream() {

			@Override
			public void write(int b) throws IOException {
				builder.append(new Character((char) b));
			}

			@Override
			public void setWriteListener(WriteListener listener) {
			}

			@Override
			public boolean isReady() {
				return true;
			}
		};
	}

	public ServletInputStream getInputStream() {
		ByteArrayInputStream body = new ByteArrayInputStream(
				builder.toString().getBytes());
		return new ServletInputStream() {

			@Override
			public int read() throws IOException {
				return body.read();
			}

			@Override
			public void setReadListener(ReadListener listener) {
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public boolean isFinished() {
				return body.available() <= 0;
			}
		};
	}

}
