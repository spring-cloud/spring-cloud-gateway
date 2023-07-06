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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.FilterDiscoverer;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerDiscoverer;
import org.springframework.cloud.gateway.server.mvc.invoke.InvocationContext;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationArgumentResolver;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameters;
import org.springframework.cloud.gateway.server.mvc.invoke.ParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.OperationMethod;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.ReflectiveOperationInvoker;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateDiscoverer;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogMessage;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RouterFunctions.route;

public class GatewayMvcPropertiesBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

	private static final RouterFunction<ServerResponse> NEVER_ROUTE = RouterFunctions.route(request -> false,
			request -> ServerResponse.notFound().build());

	protected final Log log = LogFactory.getLog(getClass());

	private final TrueNullOperationArgumentResolver trueNullOperationArgumentResolver = new TrueNullOperationArgumentResolver();

	private final Environment env;

	private final FilterDiscoverer filterDiscoverer = new FilterDiscoverer();

	private final HandlerDiscoverer handlerDiscoverer = new HandlerDiscoverer();

	private final PredicateDiscoverer predicateDiscoverer = new PredicateDiscoverer();

	private final ParameterValueMapper parameterValueMapper = new ConversionServiceParameterValueMapper();

	public GatewayMvcPropertiesBeanDefinitionRegistrar(Environment env) {
		this.env = env;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		// registers a RouterFunctionHolder that specifically isn't a RouterFunction since
		// RouterFunctionMapping gets a list of RouterFunction and if you put
		// RouterFunction in refresh scope, RouterFunctionMapping will end up with two.
		// Uses this::routerFunctionHolderSupplier so when the bean is refreshed, that
		// method is called again.
		AbstractBeanDefinition routerFnProviderBeanDefinition = BeanDefinitionBuilder
				.genericBeanDefinition(RouterFunctionHolder.class, this::routerFunctionHolderSupplier)
				.getBeanDefinition();
		// TODO: opt out of refresh scope?
		// Puts the RouterFunctionHolder in refresh scope
		BeanDefinitionHolder holder = new BeanDefinitionHolder(routerFnProviderBeanDefinition,
				"gatewayRouterFunctionHolder");
		BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
		routerFnProviderBeanDefinition.setScope("refresh");
		if (registry.containsBeanDefinition(proxy.getBeanName())) {
			registry.removeBeanDefinition(proxy.getBeanName());
		}
		registry.registerBeanDefinition(proxy.getBeanName(), proxy.getBeanDefinition());

		// registers a DelegatingRouterFunction(RouterFunctionHolder) bean this way the
		// holder can be refreshed and all config based routes will be reloaded.

		AbstractBeanDefinition routerFunctionBeanDefinition = BeanDefinitionBuilder
				.genericBeanDefinition(DelegatingRouterFunction.class).getBeanDefinition();
		registry.registerBeanDefinition("gatewayCompositeRouterFunction", routerFunctionBeanDefinition);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private RouterFunctionHolder routerFunctionHolderSupplier() {
		GatewayMvcProperties properties = Binder.get(env).bindOrCreate(GatewayMvcProperties.PREFIX,
				GatewayMvcProperties.class);
		log.trace(LogMessage.format("RouterFunctionHolder initializing with %d map routes and %d list routes",
				properties.getRoutesMap().size(), properties.getRoutes().size()));

		Map<String, RouterFunction> routerFunctions = new LinkedHashMap<>();
		properties.getRoutes().forEach(routeProperties -> {
			routerFunctions.put(routeProperties.getId(), getRouterFunction(routeProperties, routeProperties.getId()));
		});
		properties.getRoutesMap().forEach((routeId, routeProperties) -> {
			String computedRouteId = routeId;
			if (StringUtils.hasText(routeProperties.getId())) {
				computedRouteId = routeProperties.getId();
			}
			routerFunctions.put(computedRouteId, getRouterFunction(routeProperties, computedRouteId));
		});
		RouterFunction routerFunction;
		if (routerFunctions.isEmpty()) {
			// no properties routes, so a RouterFunction that will never match
			routerFunction = NEVER_ROUTE;
		}
		else {
			routerFunction = routerFunctions.values().stream().reduce(RouterFunction::andOther).orElse(null);
			// puts the map of configured RouterFunctions in an attribute. Makes testing
			// easy.
			routerFunction = routerFunction.withAttribute("gatewayRouterFunctions", routerFunctions);
		}
		log.trace(LogMessage.format("RouterFunctionHolder initialized %s", routerFunction.toString()));
		return new RouterFunctionHolder(routerFunction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private RouterFunction getRouterFunction(RouteProperties routeProperties, String routeId) {
		log.trace(LogMessage.format("Creating route for : %s", routeProperties));

		RouterFunctions.Builder builder = route();

		// MVC.fn users won't need this anonymous filter as url will be set directly.
		// Put this function first, so if a filter from a handler changes the url
		// it is after this one.
		builder.filter((request, next) -> {
			MvcUtils.setRequestUrl(request, routeProperties.getUri());
			MvcUtils.setRouteId(request, routeId);
			return next.handle(request);
		});

		MultiValueMap<String, OperationMethod> handlerOperations = handlerDiscoverer.getOperations();
		// TODO: cache?
		// translate handlerFunction
		String scheme = routeProperties.getUri().getScheme();
		Map<String, String> handlerArgs = new HashMap<>();
		// TODO: avoid hardcoded scheme/uri args
		// maybe find empty args or single RouteProperties param?
		if (scheme.equals("lb")) {
			handlerArgs.put("uri", routeProperties.getUri().toString());
		}
		Optional<NormalizedOperationMethod> handlerOperationMethod = findOperation(handlerOperations,
				scheme.toLowerCase(), handlerArgs);
		if (handlerOperationMethod.isEmpty()) {
			throw new IllegalStateException("Unable to find HandlerFunction for scheme: " + scheme);
		}
		NormalizedOperationMethod normalizedOpMethod = handlerOperationMethod.get();
		Object response = invokeOperation(normalizedOpMethod, normalizedOpMethod.getNormalizedArgs());
		HandlerFunction<ServerResponse> handlerFunction = null;
		if (response instanceof HandlerFunction<?>) {
			handlerFunction = (HandlerFunction<ServerResponse>) response;
		}
		else if (response instanceof HandlerDiscoverer.Result result) {
			handlerFunction = result.getHandlerFunction();
			result.getFilters().forEach(builder::filter);
		}
		if (handlerFunction == null) {
			throw new IllegalStateException(
					"Unable to find HandlerFunction for scheme: " + scheme + " and response " + response);
		}

		// translate predicates
		MultiValueMap<String, OperationMethod> predicateOperations = predicateDiscoverer.getOperations();
		final AtomicReference<RequestPredicate> predicate = new AtomicReference<>();

		routeProperties.getPredicates()
				.forEach(predicateProperties -> translate(predicateOperations, predicateProperties.getName(),
						predicateProperties.getArgs(), RequestPredicate.class, requestPredicate -> {
							log.trace(LogMessage.format("Adding predicate to route %s - %s", routeId,
									predicateProperties));
							if (predicate.get() == null) {
								predicate.set(requestPredicate);
							}
							else {
								RequestPredicate combined = predicate.get().and(requestPredicate);
								predicate.set(combined);
							}
							log.trace(LogMessage.format("Combined predicate for route %s - %s", routeId,
									predicate.get()));
						}));

		// combine predicate and handlerFunction
		builder.route(predicate.get(), handlerFunction);
		predicate.set(null);

		// translate filters
		MultiValueMap<String, OperationMethod> filterOperations = filterDiscoverer.getOperations();
		routeProperties.getFilters().forEach(filterProperties -> translate(filterOperations, filterProperties.getName(),
				filterProperties.getArgs(), HandlerFilterFunction.class, builder::filter));

		builder.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, routeId);

		return builder.build();
	}

	private <T> void translate(MultiValueMap<String, OperationMethod> operations, String operationName,
			Map<String, String> operationArgs, Class<T> returnType, Consumer<T> operationHandler) {
		String normalizedName = StringUtils.uncapitalize(operationName);
		Optional<NormalizedOperationMethod> operationMethod = findOperation(operations, normalizedName, operationArgs);
		if (operationMethod.isPresent()) {
			NormalizedOperationMethod opMethod = operationMethod.get();
			T handlerFilterFunction = invokeOperation(opMethod, opMethod.getNormalizedArgs());
			if (handlerFilterFunction != null) {
				operationHandler.accept(handlerFilterFunction);
			}
		}
		else {
			throw new IllegalArgumentException(String.format("Unable to find operation %s for %s with args %s",
					returnType, normalizedName, operationArgs));
		}
	}

	private Optional<NormalizedOperationMethod> findOperation(MultiValueMap<String, OperationMethod> operations,
			String operationName, Map<String, String> operationArgs) {
		return operations.getOrDefault(operationName, Collections.emptyList()).stream()
				.map(operationMethod -> new NormalizedOperationMethod(operationMethod, operationArgs))
				.filter(opeMethod -> matchOperation(opeMethod, operationArgs)).findFirst();
	}

	private static boolean matchOperation(NormalizedOperationMethod operationMethod, Map<String, String> args) {
		Map<String, String> normalizedArgs = operationMethod.getNormalizedArgs();
		OperationParameters parameters = operationMethod.getParameters();
		if (parameters.getParameterCount() != normalizedArgs.size()) {
			return false;
		}
		for (int i = 0; i < parameters.getParameterCount(); i++) {
			if (!normalizedArgs.containsKey(parameters.get(i).getName())) {
				return false;
			}
		}
		// args contains all parameter names
		return true;
	}

	private <T> T invokeOperation(OperationMethod operationMethod, Map<String, String> operationArgs) {
		Map<String, Object> args = new HashMap<>(operationArgs);
		ReflectiveOperationInvoker operationInvoker = new ReflectiveOperationInvoker(operationMethod,
				this.parameterValueMapper);
		InvocationContext context = new InvocationContext(args, trueNullOperationArgumentResolver);
		return operationInvoker.invoke(context);
	}

	static class TrueNullOperationArgumentResolver implements OperationArgumentResolver {

		@Override
		public boolean canResolve(Class<?> type) {
			return true;
		}

		@Override
		public <T> T resolve(Class<T> type) {
			return null;
		}

	}

	/**
	 * Simply holds the composite gateway RouterFunction. This class can be refresh scope
	 * without fear of having multiple RouterFunction mappings.
	 */
	static class RouterFunctionHolder {

		private final RouterFunction<ServerResponse> routerFunction;

		RouterFunctionHolder(RouterFunction<ServerResponse> routerFunction) {
			this.routerFunction = routerFunction;
		}

		public RouterFunction<ServerResponse> getRouterFunction() {
			return this.routerFunction;
		}

	}

	/**
	 * Delegating RouterFunction impl that delegates to the refreshable
	 * RouterFunctionHolder.
	 */
	static class DelegatingRouterFunction implements RouterFunction<ServerResponse> {

		final RouterFunctionHolder provider;

		DelegatingRouterFunction(RouterFunctionHolder provider) {
			this.provider = provider;
		}

		@Override
		public RouterFunction<ServerResponse> and(RouterFunction<ServerResponse> other) {
			return this.provider.getRouterFunction().and(other);
		}

		@Override
		public RouterFunction<?> andOther(RouterFunction<?> other) {
			return this.provider.getRouterFunction().andOther(other);
		}

		@Override
		public RouterFunction<ServerResponse> andRoute(RequestPredicate predicate,
				HandlerFunction<ServerResponse> handlerFunction) {
			return this.provider.getRouterFunction().andRoute(predicate, handlerFunction);
		}

		@Override
		public RouterFunction<ServerResponse> andNest(RequestPredicate predicate,
				RouterFunction<ServerResponse> routerFunction) {
			return this.provider.getRouterFunction().andNest(predicate, routerFunction);
		}

		@Override
		public <S extends ServerResponse> RouterFunction<S> filter(
				HandlerFilterFunction<ServerResponse, S> filterFunction) {
			return this.provider.getRouterFunction().filter(filterFunction);
		}

		@Override
		public void accept(RouterFunctions.Visitor visitor) {
			this.provider.getRouterFunction().accept(visitor);
		}

		@Override
		public RouterFunction<ServerResponse> withAttribute(String name, Object value) {
			return this.provider.getRouterFunction().withAttribute(name, value);
		}

		@Override
		public RouterFunction<ServerResponse> withAttributes(Consumer<Map<String, Object>> attributesConsumer) {
			return this.provider.getRouterFunction().withAttributes(attributesConsumer);
		}

		@Override
		public Optional<HandlerFunction<ServerResponse>> route(ServerRequest request) {
			return this.provider.getRouterFunction().route(request);
		}

		@Override
		public String toString() {
			return this.provider.getRouterFunction().toString();
		}

	}

}
