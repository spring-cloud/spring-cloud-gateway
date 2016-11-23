package org.springframework.cloud.gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.GatewayProperties.Route;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class GatewayHostHandlerMapping extends AbstractHandlerMapping {

	private GatewayProperties properties;
	private WebHandler webHandler;

	private final Map<String, Object> handlerMap = new LinkedHashMap<>();

	public GatewayHostHandlerMapping(GatewayProperties properties, WebHandler webHandler) {
		this.properties = properties;
		this.webHandler = webHandler;
	}

	@Override
	protected void initApplicationContext() throws BeansException {
		super.initApplicationContext();
		registerHandlers(this.properties.getRoutes());
		setPathMatcher(new AntPathMatcher("."));
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		String host = exchange.getRequest().getHeaders().getFirst("Host");
		Object handler;
		try {
			handler = lookupHandler(host, exchange);
		}
		catch (Exception ex) {
			return Mono.error(ex);
		}

		if (handler != null && logger.isDebugEnabled()) {
			logger.debug("Mapping [" + host + "] to " + handler);
		}
		else if (handler == null && logger.isTraceEnabled()) {
			logger.trace("No handler mapping found for [" + host + "]");
		}

		return Mono.justOrEmpty(handler);
	}


	/**
	 * Look up a handler instance for the given URL path.
	 *
	 * <p>Supports direct matches, e.g. a registered "/test" matches "/test",
	 * and various Ant-style pattern matches, e.g. a registered "/t*" matches
	 * both "/test" and "/team". For details, see the AntPathMatcher class.
	 *
	 * <p>Looks for the most exact pattern, where most exact is defined as
	 * the longest path pattern.
	 *
	 * @param host URL the bean is mapped to
	 * @param exchange the current exchange
	 * @return the associated handler instance, or {@code null} if not found
	 * @see org.springframework.util.AntPathMatcher
	 */
	protected Object lookupHandler(String host, ServerWebExchange exchange) throws Exception {
		// Direct match?
		Object handler = this.handlerMap.get(host);
		if (handler != null) {
			return handleMatch(handler, host, exchange);
		}
		// Pattern match?
		List<String> matches = new ArrayList<>();
		for (String pattern : this.handlerMap.keySet()) {
			if (getPathMatcher().match(pattern, host)) {
				matches.add(pattern);
			}
		}
		String bestMatch = null;
		Comparator<String> comparator = getPathMatcher().getPatternComparator(host);
		if (!matches.isEmpty()) {
			Collections.sort(matches, comparator);
			if (logger.isDebugEnabled()) {
				logger.debug("Matching patterns for request [" + host + "] are " + matches);
			}
			bestMatch = matches.get(0);
		}
		if (bestMatch != null) {
			handler = this.handlerMap.get(bestMatch);
			if (handler == null) {
				Assert.isTrue(bestMatch.endsWith("/"));
				handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
			}
			return handleMatch(handler, bestMatch, exchange);
		}
		// No handler found...
		return null;
	}


	private Object handleMatch(Object handler, String bestMatch,
							   ServerWebExchange exchange) throws Exception {

		// Bean name or resolved handler?
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = getApplicationContext().getBean(handlerName);
		}

		if (handler instanceof RouteHolder) {
			RouteHolder holder = (RouteHolder) handler;
			exchange.getAttributes().put("gatewayRoute", holder.route);
			return holder.webHandler;
		}

		validateHandler(handler, exchange);

		exchange.getAttributes().put(BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatch);

		return handler;
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param handler the handler object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateHandler(Object handler, ServerWebExchange exchange) throws Exception {
	}


	protected void registerHandlers(Map<String, Route> routes) {
		for (Route route : routes.values()) {
			if (StringUtils.hasText(route.getRequestHost())) {
				registerHandler(route.getRequestHost(), new RouteHolder(route, this.webHandler));
			}
		}
	}

	/**
	 * Register the specified handler for the given host path.
	 * @param hostPath the host the bean should be mapped to
	 * @param handler the handler instance or handler bean name String
	 * (a bean name will automatically be resolved into the corresponding handler bean)
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String hostPath, Object handler) throws BeansException, IllegalStateException {
		Assert.notNull(hostPath, "host path must not be null");
		Assert.notNull(handler, "Handler object must not be null");
		Object resolvedHandler = handler;

		// Eagerly resolve handler if referencing singleton via name.
		/*if (!this.lazyInitHandlers && handler instanceof String) {
			String handlerName = (String) handler;
			if (getApplicationContext().isSingleton(handlerName)) {
				resolvedHandler = getApplicationContext().getBean(handlerName);
			}
		}*/

		Object mappedHandler = this.handlerMap.get(hostPath);
		if (mappedHandler != null) {
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						"Cannot map " + getHandlerDescription(handler) + " to host path [" + hostPath +
								"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		}
		else {
			this.handlerMap.put(hostPath, resolvedHandler);
			if (logger.isInfoEnabled()) {
				logger.info("Mapped host path [" + hostPath + "] onto " + getHandlerDescription(handler));
			}
		}
	}


	private String getHandlerDescription(Object handler) {
		String desc;
		if (handler instanceof String) {
			desc = "'" + handler + "'";
		} else if (handler instanceof RouteHolder) {
			desc = "of type [" + ((RouteHolder) handler).webHandler.getClass() + "]";
		} else {
			desc = "of type [" + handler.getClass() + "]";
		}
		return "handler " + desc;
	}

	private class RouteHolder {
		private final Route route;
		private final WebHandler webHandler;

		public RouteHolder(Route route, WebHandler webHandler) {
			this.route = route;
			this.webHandler = webHandler;
		}
	}

}
