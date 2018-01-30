/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixObservableCommand.Setter;
import com.netflix.hystrix.exception.HystrixRuntimeException;

import static com.netflix.hystrix.exception.HystrixRuntimeException.FailureType.TIMEOUT;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedQuery;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;
import rx.Subscription;

/**
 * @author Spencer Gibb
 */
public class HystrixGatewayFilterFactory implements GatewayFilterFactory {

	public static final String FALLBACK_URI = "fallbackUri";

	private final DispatcherHandler dispatcherHandler;

	public HystrixGatewayFilterFactory(DispatcherHandler dispatcherHandler) {
		this.dispatcherHandler = dispatcherHandler;
	}

	@Override
	public List<String> argNames() {
		return Arrays.asList(NAME_KEY);
	}

	@Override
	public boolean validateArgs() {
		return false;
	}

	@Override
	public GatewayFilter apply(Tuple args) {
		//TODO: if no name is supplied, generate one from command id (useful for default filter)
		String commandName = args.getString(NAME_KEY);
		if (args.hasFieldName(FALLBACK_URI)) {
			URI fallbackUri = URI.create(args.getString(FALLBACK_URI));
			if (!"forward".equals(fallbackUri.getScheme())) {
				throw new IllegalArgumentException("Hystrix Filter currently only supports 'forward' URIs, found "+ fallbackUri);
			}
			return apply(commandName, fallbackUri);
		}
		return apply(commandName, null);
	}

	public GatewayFilter apply(String commandName) {
		return apply(commandName, null);
	}

	public GatewayFilter apply(String commandName, URI fallbackUri) {
		final HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey(getClass().getSimpleName());
		final HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(commandName);

		final Setter setter = Setter.withGroupKey(groupKey)
				.andCommandKey(commandKey);
		return apply(setter, fallbackUri);
	}

	public GatewayFilter apply(Setter setter) {
		return apply(setter, null);
	}

	public GatewayFilter apply(Setter setter, URI fallbackUri) {
		return (exchange, chain) -> {
			RouteHystrixCommand command = new RouteHystrixCommand(setter, fallbackUri, exchange, chain);

			return Mono.create(s -> {
				Subscription sub = command.toObservable().subscribe(s::success, s::error, s::success);
				s.onCancel(sub::unsubscribe);
			}).onErrorResume((Function<Throwable, Mono<Void>>) throwable -> {
				if (throwable instanceof HystrixRuntimeException) {
					HystrixRuntimeException e = (HystrixRuntimeException) throwable;
					if (e.getFailureType() == TIMEOUT) { //TODO: optionally set status
						setResponseStatus(exchange, HttpStatus.GATEWAY_TIMEOUT);
						return exchange.getResponse().setComplete();
					}
				}
				return Mono.empty();
			}).then();
		};
	}

	//TODO: replace with HystrixMonoCommand that we write
	private class RouteHystrixCommand extends HystrixObservableCommand<Void> {

		private final URI fallbackUri;
		private final ServerWebExchange exchange;
		private final GatewayFilterChain chain;

		RouteHystrixCommand(Setter setter, URI fallbackUri, ServerWebExchange exchange, GatewayFilterChain chain) {
			super(setter);
			this.fallbackUri = fallbackUri;
			this.exchange = exchange;
			this.chain = chain;
		}

		@Override
		protected Observable<Void> construct() {
			return RxReactiveStreams.toObservable(this.chain.filter(exchange));
		}

		@Override
		protected Observable<Void> resumeWithFallback() {
			if (this.fallbackUri == null) {
				return super.resumeWithFallback();
			}

			//TODO: copied from RouteToRequestUrlFilter
			URI uri = exchange.getRequest().getURI();
			//TODO: assume always?
			boolean encoded = containsEncodedQuery(uri);
			URI requestUrl = UriComponentsBuilder.fromUri(uri)
					.host(null)
					.port(null)
					.uri(this.fallbackUri)
					.build(encoded)
					.toUri();
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);

			ServerHttpRequest request = mutate(this.exchange.getRequest()).uri(requestUrl).build();
			ServerWebExchange mutated = exchange.mutate().request(request).build();
			return RxReactiveStreams.toObservable(HystrixGatewayFilterFactory.this.dispatcherHandler.handle(mutated));
		}
	}
}
