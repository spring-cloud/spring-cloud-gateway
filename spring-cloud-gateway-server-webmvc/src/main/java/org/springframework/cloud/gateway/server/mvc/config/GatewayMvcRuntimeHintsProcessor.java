/*
 * Copyright 2013-present the original author or authors.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.gateway.server.mvc.filter.FilterAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateAutoConfiguration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * A {@link BeanFactoryInitializationAotProcessor} responsible for registering reflection
 * hints for Gateway MVC beans.
 *
 * @author Jürgen Wißkirchen
 * @author Olga Maciaszek-Sharma
 * @since 4.3.0
 */
public class GatewayMvcRuntimeHintsProcessor implements BeanFactoryInitializationAotProcessor {

	private static final Log LOG = LogFactory.getLog(GatewayMvcRuntimeHintsProcessor.class);

	private static final String GATEWAY_MVC_FILTER_PACKAGE_NAME = "org.springframework.cloud.gateway.server.mvc.filter";

	private static final String GATEWAY_MVC_PREDICATE_PACKAGE_NAME = "org.springframework.cloud.gateway.server.mvc.predicate";

	private static final Map<String, Set<String>> beansConditionalOnClasses = Map.of(
			"io.github.bucket4j.BucketConfiguration",
			Set.of("org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions"),
			"org.springframework.cloud.client.circuitbreaker.CircuitBreaker",
			Set.of("org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions"),
			"org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient",
			Set.of("org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions"),
			"org.springframework.retry.support.RetryTemplate",
			Set.of("org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions"),
			"org.springframework.security.oauth2.client.OAuth2AuthorizedClient",
			Set.of("org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions"));

	private static final Set<Class<?>> PROPERTIES = Set.of(FilterProperties.class, PredicateProperties.class,
			RouteProperties.class);

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (generationContext, beanFactoryInitializationCode) -> {
			ReflectionHints hints = generationContext.getRuntimeHints().reflection();
			Set<Class<?>> typesToRegister = Stream
				.of(getTypesToRegister(GATEWAY_MVC_FILTER_PACKAGE_NAME),
						getTypesToRegister(GATEWAY_MVC_PREDICATE_PACKAGE_NAME), PROPERTIES,
						new HashSet<>(Collections.singletonList(FilterFunctions.class)))
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
			typesToRegister.forEach(clazz -> hints.registerType(TypeReference.of(clazz),
					hint -> hint.withMembers(MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
							MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)));
		};
	}

	private static Set<Class<?>> getTypesToRegister(String packageName) {
		Set<Class<?>> classesToAdd = getClassesToAdd(packageName);
		Set<Class<?>> genericsToAdd = new HashSet<>();
		Set<Class<?>> superTypes = new HashSet<>();
		Set<Class<?>> enclosingClasses = new HashSet<>();
		for (Class<?> clazz : classesToAdd) {
			ResolvableType resolvableType = ResolvableType.forType(clazz);
			addGenericsForClass(genericsToAdd, resolvableType);
			addSuperTypesForClass(resolvableType, superTypes, genericsToAdd);
			addEnclosingClassesForClass(enclosingClasses, resolvableType.getRawClass());
		}
		classesToAdd.addAll(genericsToAdd);
		classesToAdd.addAll(superTypes);
		classesToAdd.addAll(enclosingClasses);
		return classesToAdd.stream().filter(Objects::nonNull).collect(Collectors.toSet());
	}

	private static void addEnclosingClassesForClass(Set<Class<?>> enclosingClasses, @Nullable Class<?> clazz) {
		if (clazz == null) {
			return;
		}
		Class<?> enclosing = clazz.getEnclosingClass();
		if (enclosing != null) {
			enclosingClasses.add(enclosing);
			addEnclosingClassesForClass(enclosingClasses, enclosing);
		}
	}

	private static Set<Class<?>> getClassesToAdd(String packageName) {
		Set<Class<?>> classesToAdd = new HashSet<>();
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AssignableTypeFilter(Object.class));
		provider.addExcludeFilter(new AssignableTypeFilter(FilterAutoConfiguration.class));
		provider.addExcludeFilter(new AssignableTypeFilter(PredicateAutoConfiguration.class));
		Set<BeanDefinition> components = provider.findCandidateComponents(packageName);
		for (BeanDefinition component : components) {
			Class<?> clazz;
			try {
				clazz = Class.forName(component.getBeanClassName());
				if (shouldRegisterClass(clazz)) {
					classesToAdd.add(clazz);
				}
			}
			catch (NoClassDefFoundError | ClassNotFoundException exception) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(exception);
				}
			}
		}
		return classesToAdd;
	}

	private static void addGenericsForClass(Set<Class<?>> genericsToAdd, ResolvableType resolvableType) {
		if (resolvableType.getSuperType().hasGenerics()) {
			genericsToAdd.addAll(Arrays.stream(resolvableType.getSuperType().getGenerics())
				.map(ResolvableType::toClass)
				.collect(Collectors.toSet()));
		}
	}

	private static void addSuperTypesForClass(ResolvableType resolvableType, Set<Class<?>> supertypesToAdd,
			Set<Class<?>> genericsToAdd) {
		ResolvableType superType = resolvableType.getSuperType();
		if (!ResolvableType.NONE.equals(superType)) {
			addGenericsForClass(genericsToAdd, superType);
			supertypesToAdd.add(superType.toClass());
			addSuperTypesForClass(superType, supertypesToAdd, genericsToAdd);
		}
	}

	private static boolean shouldRegisterClass(Class<?> clazz) {
		Set<String> conditionClasses = beansConditionalOnClasses.getOrDefault(clazz.getName(), Collections.emptySet());
		for (String conditionClass : conditionClasses) {
			try {
				GatewayMvcRuntimeHintsProcessor.class.getClassLoader().loadClass(conditionClass);
			}
			catch (ClassNotFoundException e) {
				return false;
			}
		}
		return true;
	}

}
