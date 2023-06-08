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

package org.springframework.cloud.gateway.server.mvc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.server.mvc.invoke.InvocationContext;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationArgumentResolver;
import org.springframework.cloud.gateway.server.mvc.invoke.ParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.OperationMethod;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.ReflectiveOperationInvoker;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RouterFunctions.route;

public class GatewayMvcPropertiesBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

	private final TrueNullOperationArgumentResolver trueNullOperationArgumentResolver = new TrueNullOperationArgumentResolver();

	private final Environment env;

	private final PredicateDiscoverer predicateDiscoverer = new PredicateDiscoverer();

	private final FilterDiscoverer filterDiscoverer = new FilterDiscoverer();

	private final ParameterValueMapper parameterValueMapper = new ConversionServiceParameterValueMapper();

	private final Binder binder;

	public GatewayMvcPropertiesBeanDefinitionRegistrar(Environment env) {
		this.env = env;
		binder = Binder.get(env);
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		GatewayMvcProperties properties = binder.bindOrCreate(GatewayMvcProperties.PREFIX, GatewayMvcProperties.class);
		properties.getRoutes().forEach(routeProperties -> {
			registerRoute(registry, routeProperties, routeProperties.getId());
		});
		properties.getRoutesMap().forEach((routeId, routeProperties) -> {
			String beanNamePrefix = routeId;
			if (StringUtils.hasText(routeProperties.getId())) {
				beanNamePrefix = routeProperties.getId();
			}
			registerRoute(registry, routeProperties, beanNamePrefix);
		});
	}

	private void registerRoute(BeanDefinitionRegistry registry, RouteProperties routeProperties,
			String beanNamePrefix) {
		AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
				.genericBeanDefinition(RouterFunction.class, () -> getRouterFunctionSupplier(routeProperties))
				.getBeanDefinition();
		registry.registerBeanDefinition(beanNamePrefix + "RouterFunction", beanDefinition);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private RouterFunction getRouterFunctionSupplier(RouteProperties routeProperties) {
		RouterFunctions.Builder builder = route();
		String scheme = routeProperties.getUri().getScheme();
		// TODO: cache, externalize?
		Method handlerFunctionMethod = ReflectionUtils.findMethod(HandlerFunctions.class, scheme);
		if (handlerFunctionMethod == null) {
			throw new IllegalStateException("Unable to find HandlerFunction for scheme: " + scheme);
		}
		HandlerFunction<ServerResponse> handlerFunction = (HandlerFunction) ReflectionUtils
				.invokeMethod(handlerFunctionMethod, null);
		// TODO: translate predicates
		Map<String, OperationMethod> predicateOperations = predicateDiscoverer.getOperations();
		RequestPredicate predicate = null;

		for (PredicateProperties predicateProperties : routeProperties.getPredicates()) {
			String predicateName = StringUtils.uncapitalize(predicateProperties.getName());
			// TODO: match by name, the number of parameters, then param name
			OperationMethod operationMethod = predicateOperations.get(predicateName);
			if (operationMethod != null) {
				ReflectiveOperationInvoker operationInvoker = new ReflectiveOperationInvoker(operationMethod,
						this.parameterValueMapper);
				Map<String, Object> args = new HashMap<>();
				args.putAll(predicateProperties.getArgs());
				InvocationContext context = new InvocationContext(args, trueNullOperationArgumentResolver);
				RequestPredicate requestPredicate = operationInvoker.invoke(context);
				if (predicate == null) {
					predicate = requestPredicate;
				}
				else {
					predicate.and(requestPredicate);
				}
			}
		}
		builder.route(predicate, handlerFunction);

		builder.filter((request, next) -> {
			request.attributes().put("routeUri", routeProperties.getUri());
			return next.handle(request);
		});

		Map<String, OperationMethod> filterOperations = filterDiscoverer.getOperations();
		// translate filters
		for (FilterProperties filterProperties : routeProperties.getFilters()) {
			String filterName = StringUtils.uncapitalize(filterProperties.getName());
			OperationMethod operationMethod = filterOperations.get(filterName);
			if (operationMethod != null) {
				ReflectiveOperationInvoker operationInvoker = new ReflectiveOperationInvoker(operationMethod,
						this.parameterValueMapper);
				Map<String, Object> args = new HashMap<>();
				args.putAll(filterProperties.getArgs());
				InvocationContext context = new InvocationContext(args, trueNullOperationArgumentResolver);
				HandlerFilterFunction handlerFilterFunction = operationInvoker.invoke(context);
				if (handlerFilterFunction != null) {
					builder.filter(handlerFilterFunction);
				}
			}
			else {
				System.err.println("Unable to find filter for " + filterProperties);
			}
		}
		return builder.build();
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
