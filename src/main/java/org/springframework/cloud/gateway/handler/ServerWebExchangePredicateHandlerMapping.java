package org.springframework.cloud.gateway.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.cloud.gateway.config.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicateFactory;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.filter.GatewayFilter.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.filter.GatewayFilter.GATEWAY_ROUTE_ATTR;

/**
 * @author Spencer Gibb
 */
public class ServerWebExchangePredicateHandlerMapping extends AbstractHandlerMapping {

	private Map<String, GatewayPredicateFactory> predicateFactories = new LinkedHashMap<>();
	private GatewayProperties properties;
	private WebHandler webHandler;

	private List<Route> routes;

	public ServerWebExchangePredicateHandlerMapping(WebHandler webHandler, List<GatewayPredicateFactory> predicateFactories, GatewayProperties properties) {
		this.webHandler = webHandler;
		this.properties = properties;

		for (GatewayPredicateFactory factory : predicateFactories) {
			if (this.predicateFactories.containsKey(factory.getName())) {
				this.logger.warn("A GatewayPredicateFactory named "+ factory.getName()
						+ " already exists, class: " + this.predicateFactories.get(factory.getName())
						+ ". It will be overwritten.");
			}
			this.predicateFactories.put(factory.getName(), factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded GatewayPredicateFactory [" + factory.getName() + "]");
			}
		}

		setOrder(-1);
	}

	@Override
	protected void initApplicationContext() throws BeansException {
		super.initApplicationContext();
		//TODO: move properties.getRoutes() to interface/impl
		registerHandlers(this.properties.getRoutes());
	}

	protected void registerHandlers(List<Route> routes) {
		this.routes = routes;
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getClass().getSimpleName());

		Route route;
		try {
			route = lookupRoute(this.routes, exchange);
		}
		catch (Exception ex) {
			return Mono.error(ex);
		}

		if (route != null) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + route);
			}

			exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
			return Mono.just(this.webHandler);
		}
		else if (this.logger.isTraceEnabled()) {
			this.logger.trace("No Route found for [" + getExchangeDesc(exchange) + "]");
		}

		return Mono.empty();
	}

	//TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}


	protected Route lookupRoute(List<Route> routes, ServerWebExchange exchange) throws Exception {
		for (Route route : routes) {
			if (!route.getPredicates().isEmpty()) {
				//TODO: cache predicate
				Predicate<ServerWebExchange> predicate = combinePredicates(route);
				if (predicate.test(exchange)) {
					validateRoute(route, exchange);
					return route;
				}
			}
		}
		return null;
	}


	private Predicate<ServerWebExchange> combinePredicates(Route route) {
		List<PredicateDefinition> predicates = route.getPredicates();
		Predicate<ServerWebExchange> predicate = lookup(predicates.get(0));

		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			Predicate<ServerWebExchange> found = lookup(andPredicate);
			predicate = predicate.and(found);
		}

		return predicate;
	}

	private Predicate<ServerWebExchange> lookup(PredicateDefinition predicate) {
		GatewayPredicateFactory found = this.predicateFactories.get(predicate.getName());
		if (found == null) {
			throw new IllegalArgumentException("Unable to find GatewayPredicateFactory with name " + predicate.getName());
		}
		return found.create(predicate.getValue(), predicate.getArgs());
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) throws Exception {
	}

}
