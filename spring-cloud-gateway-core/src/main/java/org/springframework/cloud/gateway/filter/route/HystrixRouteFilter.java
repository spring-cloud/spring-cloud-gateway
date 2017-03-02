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

package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;

import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;
import rx.Subscription;

/**
 * @author Spencer Gibb
 */
public class HystrixRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String... args) {
		validate(1, args);
		final String commandName = args[0];
		final HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey(getClass().getSimpleName());
		final HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(commandName);

		final HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter
				.withGroupKey(groupKey)
				.andCommandKey(commandKey);

		return (exchange, chain) -> {
			RouteHystrixCommand command = new RouteHystrixCommand(setter, exchange, chain);

			return Mono.create(s -> {
				Subscription sub = command.toObservable().subscribe(s::success, s::error, s::success);
				s.setCancellation(sub::unsubscribe);
			});
		};
	}

	//TODO: replace with HystrixMonoCommand that we write
	private class RouteHystrixCommand extends HystrixObservableCommand<Void> {
		private final ServerWebExchange exchange;
		private final WebFilterChain chain;

		RouteHystrixCommand(Setter setter, ServerWebExchange exchange, WebFilterChain chain) {
			super(setter);
			this.exchange = exchange;
			this.chain = chain;
		}

		@Override
		protected Observable<Void> construct() {
			return RxReactiveStreams.toObservable(this.chain.filter(this.exchange));
		}
	}
}
