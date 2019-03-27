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

package org.springframework.cloud.gateway.webflux;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * A <code>@RequestMapping</code> argument type that can proxy the request to a backend.
 * Spring will inject one of these into your MVC handler method, and you get return a
 * <code>ResponseEntity</code> that you get from one of the HTTP methods {@link #get()},
 * {@link #post()}, {@link #put()}, {@link #patch()}, {@link #delete()} etc. Example:
 * 
 * <pre>
 * &#64;GetMapping("/proxy/{id}")
 * public Mono&lt;ResponseEntity&lt;?&gt;&gt; proxy(@PathVariable Integer id, ProxyExchange&lt;?&gt; proxy)
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
 * <code>byte[]</code> (<code>Object</code> probably won't work unless you provide a converter). 
 * Use a concrete type if you want to
 * transform or manipulate the response, or if you want to assert that it is convertible
 * to the type you declare.
 * </p>
 * <p>
 * To manipulate the response use the overloaded HTTP methods with a <code>Function</code>
 * argument and pass in code to transform the response. E.g.
 * 
 * <pre>
 * &#64;PostMapping("/proxy")
 * public Mono&lt;ResponseEntity&lt;Foo&gt;&gt; proxy(ProxyExchange&lt;Foo&gt; proxy) throws Exception {
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
 * also to the {@link WebClient} that is used in the backend calls (see the
 * {@link ProxyExchange#ProxyExchange(WebClient, ServerWebExchange, BindingContext, Type)
 * constructor} for details).
 * </p>
 * 
 * @author Dave Syer
 *
 */
public class ProxyExchange<T> {

	public static Set<String> DEFAULT_SENSITIVE = new HashSet<>(
			Arrays.asList("cookie", "authorization"));

	private URI uri;

	private WebClient rest;

	private Publisher<Object> body;

	private boolean hasBody = false;
	
	private ServerWebExchange exchange;
	private BindingContext bindingContext;

	private Set<String> sensitive;

	private HttpHeaders headers = new HttpHeaders();

	private Type responseType;

	public ProxyExchange(WebClient rest, ServerWebExchange exchange,
			BindingContext bindingContext, Type type) {
		this.exchange = exchange;
		this.bindingContext = bindingContext;
		this.responseType = type;
		this.rest = rest;
		this.sensitive = new HashSet<>(DEFAULT_SENSITIVE.size());
		this.sensitive.addAll(DEFAULT_SENSITIVE);
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
		this.body = Mono.just(body);
		return this;
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
	@SuppressWarnings("unchecked")
	public ProxyExchange<T> body(Publisher<?> body) {
		this.body = (Publisher<Object>) body;
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
		return exchange.getRequest().getPath().pathWithinApplication().value();
	}

	public String path(String prefix) {
		String path = path();
		if (!path.startsWith(prefix)) {
			throw new IllegalArgumentException(
					"Path does not start with prefix (" + prefix + "): " + path);
		}
		return path.substring(prefix.length());
	}

	public Mono<ResponseEntity<T>> get() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.get(uri))
				.build();
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> get(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return get().map(converter::apply);
	}

	public Mono<ResponseEntity<T>> head() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.head(uri))
				.build();
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> head(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return head().map(converter::apply);
	}

	public Mono<ResponseEntity<T>> options() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.options(uri))
				.build();
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> options(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return options().map(converter::apply);
	}

	public Mono<ResponseEntity<T>> post() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.post(uri))
				.body(body());
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> post(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return post().map(converter::apply);
	}

	public Mono<ResponseEntity<T>> delete() {
		RequestEntity<Void> requestEntity = headers(
				(BodyBuilder) RequestEntity.delete(uri)).build();
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> delete(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return delete().map(converter::apply);
	}

	public Mono<ResponseEntity<T>> put() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.put(uri))
				.body(body());
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> put(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return put().map(converter::apply);
	}

	public Mono<ResponseEntity<T>> patch() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.patch(uri))
				.body(body());
		return exchange(requestEntity);
	}

	public <S> Mono<ResponseEntity<S>> patch(
			Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return patch().map(converter::apply);
	}

	private Mono<ResponseEntity<T>> exchange(RequestEntity<?> requestEntity) {
		Type type = this.responseType;
		RequestBodySpec builder = rest.method(requestEntity.getMethod())
				.uri(requestEntity.getUrl())
				.headers(headers -> addHeaders(headers, requestEntity.getHeaders()));
		Mono<ClientResponse> result;
		if (requestEntity.getBody() instanceof Publisher) {
			@SuppressWarnings("unchecked")
			Publisher<Object> publisher = (Publisher<Object>) requestEntity.getBody();
			result = builder.body(publisher, Object.class).exchange();
		}
		else if (requestEntity.getBody() != null) {
			result = builder.body(BodyInserters.fromObject(requestEntity.getBody()))
					.exchange();
		}
		else {
			if (hasBody) {
				result = builder.headers(
						headers -> addHeaders(headers, exchange.getRequest().getHeaders()))
						.body(exchange.getRequest().getBody(), DataBuffer.class)
						.exchange();
			}
			else {
				result = builder.headers(
						headers -> addHeaders(headers, exchange.getRequest().getHeaders()))
						.exchange();
			}
		}
		return result.flatMap(response -> response.toEntity(ParameterizedTypeReference.forType(type)));
	}

	private void addHeaders(HttpHeaders headers, HttpHeaders toAdd) {
		Set<String> filteredKeys = filterHeaderKeys(toAdd);
		filteredKeys.stream()
				.filter(key -> !headers.containsKey(key))
				.forEach(header -> headers.addAll(header, toAdd.get(header)));
	}

	private Set<String> filterHeaderKeys(HttpHeaders headers) {
		return headers.keySet().stream().filter(header -> !sensitive.contains(header.toLowerCase())).collect(Collectors.toSet());
	}

	private BodyBuilder headers(BodyBuilder builder) {
		proxy();
		for (String name : filterHeaderKeys(headers)) {
			builder.header(name, headers.get(name).toArray(new String[0]));
		}
		return builder;
	}

	private void proxy() {
		URI uri = exchange.getRequest().getURI();
		appendForwarded(uri);
		appendXForwarded(uri);
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

	private Publisher<?> body() {
		Publisher<?> body = this.body;
		if (body != null) {
			return body;
		}
		body = getRequestBody();
		hasBody = true; // even if it's null
		return body;
	}

	/**
	 * Search for the request body if it was already deserialized using
	 * <code>@RequestBody</code>. If it is not found then deserialize it in the same way
	 * that it would have been for a <code>@RequestBody</code>.
	 * 
	 * @return the request body
	 */
	private Mono<Object> getRequestBody() {
		for (String key : bindingContext.getModel().asMap().keySet()) {
			if (key.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
				BindingResult result = (BindingResult) bindingContext.getModel().asMap()
						.get(key);
				return Mono.just(result.getTarget());
			}
		}
		return null;
	}

	protected static class BodyGrabber {
		public Publisher<Object> body(@RequestBody Publisher<Object> body) {
			return body;
		}
	}

	protected static class BodySender {
		@ResponseBody
		public Publisher<Object> body() {
			return null;
		}
	}

}
