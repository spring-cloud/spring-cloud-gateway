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
	public WebFilter apply(String commandName, String[] args) {
		//validate(args, 1);

		final HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey(getClass().getSimpleName());
		final HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(commandName);

		final HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter
				.withGroupKey(groupKey)
				.andCommandKey(commandKey);

		//TODO: caching can happen here
		return (exchange, chain) -> {
			try {
				RouteHystrixCommand command = new RouteHystrixCommand(setter, exchange, chain);
				final Observable<Void> observable = command.toObservable();

				return Mono.create(s -> {
					final Subscription subscription = observable.subscribe(s::success, s::error, s::success);
					s.setCancellation(subscription::unsubscribe);
				});
			} catch (Exception e) {
				throw new RuntimeException("Error running RouteHystrixCommand: " + commandName, e);
			}
		};
	}

	//TODO: replace with HystrixMonoCommand that we write
	private class RouteHystrixCommand extends HystrixObservableCommand<Void> {
		private final ServerWebExchange exchange;
		private final WebFilterChain chain;

		protected RouteHystrixCommand(Setter setter, ServerWebExchange exchange, WebFilterChain chain) {
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
