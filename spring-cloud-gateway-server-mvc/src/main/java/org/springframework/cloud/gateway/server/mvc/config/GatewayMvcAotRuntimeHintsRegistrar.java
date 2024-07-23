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

import java.util.Arrays;
import java.util.Set;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.BodyFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerHandlerSupplier;
import org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;

/**
 * AOT runtime hints registrar on the gateway server mvc.
 *
 * @author Jürgen Wißkirchen
 */
public class GatewayMvcAotRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

	private static final Set<Class<?>> FUNCTION_PROVIDERS = Set.of(HandlerFunctions.class,
			LoadBalancerHandlerSupplier.class, FilterFunctions.class, BeforeFilterFunctions.class,
			AfterFilterFunctions.class, TokenRelayFilterFunctions.class, BodyFilterFunctions.class,
			CircuitBreakerFilterFunctions.class, GatewayRouterFunctions.class, LoadBalancerFilterFunctions.class,
			GatewayRequestPredicates.class, Bucket4jFilterFunctions.class);

	private static final Set<Class<?>> PROPERTIES = Set.of(FilterProperties.class, PredicateProperties.class,
			RouteProperties.class);

	@Override
	public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
		final ReflectionHints reflectionHints = hints.reflection();
		FUNCTION_PROVIDERS.forEach(clazz -> addHintsForClass(reflectionHints, clazz, classLoader));

		PROPERTIES.forEach(clazz -> reflectionHints.registerType(clazz, MemberCategory.PUBLIC_FIELDS,
				MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
	}

	/**
	 * Add hints for the given class. Since we need to register mostly static methods, the
	 * annotation way with @Reflective does not work here.
	 * @param reflectionHints the reflection hints
	 * @param clazz the class to add hints for
	 * @param classLoader the class loader
	 */
	private void addHintsForClass(ReflectionHints reflectionHints, Class<?> clazz, ClassLoader classLoader) {
		if (!ClassUtils.isPresent(clazz.getName(), classLoader)) {
			return; // safety net
		}
		Arrays.stream(clazz.getMethods())
			.forEach(method -> reflectionHints.registerMethod(method, ExecutableMode.INVOKE));
	}

}
