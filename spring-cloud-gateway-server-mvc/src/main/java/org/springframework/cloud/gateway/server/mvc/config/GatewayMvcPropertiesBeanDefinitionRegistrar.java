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

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * BeanDefinitionRegistrar that registers a RouterFunctionHolder and a
 * DelegatingRouterFunction.
 *
 * @author Spencer Gibb
 * @author Pavel Tregl
 * @author Jürgen Wißkirchen
 */
public class GatewayMvcPropertiesBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		// registers a RouterFunctionHolder that specifically isn't a RouterFunction since
		// RouterFunctionMapping gets a list of RouterFunction and if you put
		// RouterFunction in refresh scope, RouterFunctionMapping will end up with two.
		// Registers RouterFunctionHolderFactory::routerFunctionHolderSupplier so when the
		// bean is refreshed, that method is called again.
		AbstractBeanDefinition routerFnProviderBeanDefinition = BeanDefinitionBuilder
			.rootBeanDefinition(RouterFunctionHolder.class)
			.setFactoryMethodOnBean("routerFunctionHolderSupplier", "routerFunctionHolderFactory")
			.getBeanDefinition();
		BeanDefinitionHolder holder = new BeanDefinitionHolder(routerFnProviderBeanDefinition,
				"gatewayRouterFunctionHolder");
		BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);

		// Puts the RouterFunctionHolder in refresh scope, if not disabled.
		if (registry.containsBeanDefinition("refreshScope")) {
			routerFnProviderBeanDefinition.setScope("refresh");
		}
		if (registry.containsBeanDefinition(proxy.getBeanName())) {
			registry.removeBeanDefinition(proxy.getBeanName());
		}
		registry.registerBeanDefinition(proxy.getBeanName(), proxy.getBeanDefinition());

		// registers a DelegatingRouterFunction(RouterFunctionHolder) bean this way the
		// holder can be refreshed and all config based routes will be reloaded.

		AbstractBeanDefinition routerFunctionBeanDefinition = BeanDefinitionBuilder
			.genericBeanDefinition(DelegatingRouterFunction.class)
			.getBeanDefinition();
		registry.registerBeanDefinition("gatewayCompositeRouterFunction", routerFunctionBeanDefinition);
	}

	/**
	 * Simply holds the composite gateway RouterFunction. This class can be refresh scope
	 * without fear of having multiple RouterFunction mappings.
	 */
	public static class RouterFunctionHolder {

		private final RouterFunction<ServerResponse> routerFunction;

		public RouterFunctionHolder(RouterFunction<ServerResponse> routerFunction) {
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
