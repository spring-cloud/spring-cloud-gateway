/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.FilterDiscoverer;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerDiscoverer;
import org.springframework.cloud.gateway.server.mvc.invoke.InvocationContext;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationArgumentResolver;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameter;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameters;
import org.springframework.cloud.gateway.server.mvc.invoke.ParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.OperationMethod;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.ReflectiveOperationInvoker;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateDiscoverer;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogMessage;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;

/**
 * Factory bean for the creation of a RouterFunctionHolder, that may have refresh scope.
 *
 * @author Spencer Gibb
 * @author Jürgen Wißkirchen
 */
public class RouterFunctionHolderFactory {

	private static final RequestPredicate neverPredicate = new RequestPredicate() {
		@Override
		public boolean test(ServerRequest request) {
			return false;
		}

		@Override
		public String toString() {
			return "Never";
		}
	};

	private static final RouterFunction<ServerResponse> NEVER_ROUTE = RouterFunctions.route(neverPredicate,
			request -> ServerResponse.notFound().build());

	private final Log log = LogFactory.getLog(getClass());

	private final TrueNullOperationArgumentResolver trueNullOperationArgumentResolver = new TrueNullOperationArgumentResolver();

	private final Environment env;

	private final FilterDiscoverer filterDiscoverer = new FilterDiscoverer();

	private final HandlerDiscoverer handlerDiscoverer = new HandlerDiscoverer();

	private final PredicateDiscoverer predicateDiscoverer = new PredicateDiscoverer();

	private final ParameterValueMapper parameterValueMapper = new ConversionServiceParameterValueMapper();

	public RouterFunctionHolderFactory(Environment env) {
		this.env = env;
	}

	/**
	 * supplier for RouterFunctionHolder, which is registered as factory method on the
	 * bean definition.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private GatewayMvcPropertiesBeanDefinitionRegistrar.RouterFunctionHolder routerFunctionHolderSupplier() {
		GatewayMvcProperties properties = Binder.get(env)
			.bindOrCreate(GatewayMvcProperties.PREFIX, GatewayMvcProperties.class);
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
		return new GatewayMvcPropertiesBeanDefinitionRegistrar.RouterFunctionHolder(routerFunction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private RouterFunction getRouterFunction(RouteProperties routeProperties, String routeId) {
		log.trace(LogMessage.format("Creating route for : %s", routeProperties));

		RouterFunctions.Builder builder = route(routeId);

		MultiValueMap<String, OperationMethod> handlerOperations = handlerDiscoverer.getOperations();
		// TODO: cache?
		// translate handlerFunction
		String scheme = routeProperties.getUri().getScheme();
		Map<String, Object> handlerArgs = new HashMap<>();
		Optional<NormalizedOperationMethod> handlerOperationMethod = findOperation(handlerOperations,
				scheme.toLowerCase(Locale.ROOT), handlerArgs);
		if (handlerOperationMethod.isEmpty()) {
			// single RouteProperties param
			handlerArgs.clear();
			String routePropsKey = StringUtils.uncapitalize(RouteProperties.class.getSimpleName());
			handlerArgs.put(routePropsKey, routeProperties);
			handlerOperationMethod = findOperation(handlerOperations, scheme.toLowerCase(Locale.ROOT), handlerArgs);
			if (handlerOperationMethod.isEmpty()) {
				throw new IllegalStateException("Unable to find HandlerFunction for scheme: " + scheme);
			}
		}
		NormalizedOperationMethod normalizedOpMethod = handlerOperationMethod.get();
		Object response = invokeOperation(normalizedOpMethod, normalizedOpMethod.getNormalizedArgs());
		HandlerFunction<ServerResponse> handlerFunction = null;

		// filters added by HandlerDiscoverer need to go last, so save them
		List<HandlerFilterFunction<ServerResponse, ServerResponse>> lowerPrecedenceFilters = new ArrayList<>();
		List<HandlerFilterFunction<ServerResponse, ServerResponse>> higherPrecedenceFilters = new ArrayList<>();
		if (response instanceof HandlerFunction<?>) {
			handlerFunction = (HandlerFunction<ServerResponse>) response;
		}
		else if (response instanceof HandlerDiscoverer.Result result) {
			handlerFunction = result.getHandlerFunction();
			lowerPrecedenceFilters.addAll(result.getLowerPrecedenceFilters());
			higherPrecedenceFilters.addAll(result.getHigherPrecedenceFilters());
		}
		if (handlerFunction == null) {
			throw new IllegalStateException(
					"Unable to find HandlerFunction for scheme: " + scheme + " and response " + response);
		}

		// translate predicates
		MultiValueMap<String, OperationMethod> predicateOperations = predicateDiscoverer.getOperations();
		final AtomicReference<RequestPredicate> predicate = new AtomicReference<>();

		routeProperties.getPredicates().forEach(predicateProperties -> {
			Map<String, Object> args = new LinkedHashMap<>(predicateProperties.getArgs());
			translate(predicateOperations, predicateProperties.getName(), args, RequestPredicate.class,
					requestPredicate -> {
						log.trace(LogMessage.format("Adding predicate to route %s - %s", routeId, predicateProperties));
						if (predicate.get() == null) {
							predicate.set(requestPredicate);
						}
						else {
							RequestPredicate combined = predicate.get().and(requestPredicate);
							predicate.set(combined);
						}
						log.trace(LogMessage.format("Combined predicate for route %s - %s", routeId, predicate.get()));
					});
		});

		// combine predicate and handlerFunction
		builder.route(predicate.get(), handlerFunction);
		predicate.set(null);

		// HandlerDiscoverer filters needing lower priority, so put them first
		lowerPrecedenceFilters.forEach(builder::filter);

		// translate filters
		MultiValueMap<String, OperationMethod> filterOperations = filterDiscoverer.getOperations();
		routeProperties.getFilters().forEach(filterProperties -> {
			Map<String, Object> args = new LinkedHashMap<>(filterProperties.getArgs());
			translate(filterOperations, filterProperties.getName(), args, HandlerFilterFunction.class, builder::filter);
		});

		// HandlerDiscoverer filters need higher priority, so put them last
		higherPrecedenceFilters.forEach(builder::filter);

		builder.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, routeId);

		return builder.build();
	}

	private <T> void translate(MultiValueMap<String, OperationMethod> operations, String operationName,
			Map<String, Object> operationArgs, Class<T> returnType, Consumer<T> operationHandler) {
		String normalizedName = StringUtils.uncapitalize(operationName);
		Optional<NormalizedOperationMethod> operationMethod = findOperation(operations, normalizedName, operationArgs);
		if (operationMethod.isPresent()) {
			NormalizedOperationMethod opMethod = operationMethod.get();
			T handlerFilterFunction = invokeOperation(opMethod, opMethod.getNormalizedArgs());
			if (handlerFilterFunction != null) {
				operationHandler.accept(handlerFilterFunction);
			}
			if (log.isDebugEnabled()) {
				log.debug(LogMessage.format("Yaml Properties matched Operations name: %s, args: %s, params: %s",
						normalizedName, opMethod.getNormalizedArgs().toString(),
						Arrays.toString(opMethod.getParameters().stream().toArray())));
			}
		}
		else {
			throw new IllegalArgumentException(String.format("Unable to find operation %s for %s with args %s",
					returnType, normalizedName, operationArgs));
		}
	}

	private Optional<NormalizedOperationMethod> findOperation(MultiValueMap<String, OperationMethod> operations,
			String operationName, Map<String, Object> operationArgs) {
		return operations.getOrDefault(operationName, Collections.emptyList())
			.stream()
			.sorted(Comparator.comparing(OperationMethod::isConfigurable))
			.map(operationMethod -> new NormalizedOperationMethod(operationMethod, operationArgs))
			.filter(opeMethod -> matchOperation(opeMethod, operationArgs))
			.findFirst();
	}

	private static boolean matchOperation(NormalizedOperationMethod operationMethod, Map<String, Object> args) {
		Map<String, Object> normalizedArgs = operationMethod.getNormalizedArgs();
		OperationParameters parameters = operationMethod.getParameters();
		if (operationMethod.isConfigurable()) {
			// this is a special case
			return true;
		}
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

	private <T> T invokeOperation(OperationMethod operationMethod, Map<String, Object> operationArgs) {
		Map<String, Object> args = new HashMap<>();
		if (operationMethod.isConfigurable()) {
			OperationParameter operationParameter = operationMethod.getParameters().get(0);
			Object config = bindConfigurable(operationMethod, operationArgs, operationParameter);
			args.put(operationParameter.getName(), config);
		}
		else {
			args.putAll(operationArgs);
		}
		ReflectiveOperationInvoker operationInvoker = new ReflectiveOperationInvoker(operationMethod,
				this.parameterValueMapper);
		InvocationContext context = new InvocationContext(args, trueNullOperationArgumentResolver);
		return operationInvoker.invoke(context);
	}

	private static Object bindConfigurable(OperationMethod operationMethod, Map<String, Object> args,
			OperationParameter operationParameter) {
		Class<?> configurableType = operationParameter.getType();
		Configurable configurable = operationMethod.getMethod().getAnnotation(Configurable.class);
		if (configurable != null && !configurable.value().equals(Void.class)) {
			configurableType = configurable.value();
		}
		Bindable<?> bindable = Bindable.of(configurableType);
		List<ConfigurationPropertySource> propertySources = Collections
			.singletonList(new MapConfigurationPropertySource(args));
		// TODO: potentially deal with conversion service
		Binder binder = new Binder(propertySources, null, DefaultConversionService.getSharedInstance());
		Object config = binder.bindOrCreate("", bindable, new IgnoreTopLevelConverterNotFoundBindHandler());
		return config;
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

}
