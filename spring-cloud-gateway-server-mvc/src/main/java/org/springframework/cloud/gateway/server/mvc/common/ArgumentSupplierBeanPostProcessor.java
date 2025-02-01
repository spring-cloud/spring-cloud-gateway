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

package org.springframework.cloud.gateway.server.mvc.common;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplier.ArgumentSuppliedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;

// The RequestPredicate needs to add `visitor.unknown(this)` in accept();
public class ArgumentSupplierBeanPostProcessor implements BeanPostProcessor {

	private final ApplicationEventPublisher publisher;

	public ArgumentSupplierBeanPostProcessor(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof RouterFunction<?> routerFunction) {
			PredicateVisitor predicateVisitor = new PredicateVisitor();
			RouterFunctionVisitor routerFunctionVisitor = new RouterFunctionVisitor(predicateVisitor);
			routerFunction.accept(routerFunctionVisitor);
			if (predicateVisitor.argumentSupplier != null) {
				ArgumentSuppliedEvent<?> argumentSuppliedEvent = predicateVisitor.argumentSupplier
					.getArgumentSuppliedEvent();
				if (predicateVisitor.attributes != null) {
					argumentSuppliedEvent = new AttributedArugmentSuppliedEvent<>(argumentSuppliedEvent,
							predicateVisitor.attributes);
				}
				publisher.publishEvent(argumentSuppliedEvent);
			}
		}
		return bean;
	}

	class RouterFunctionVisitor implements RouterFunctions.Visitor {

		private final PredicateVisitor predicateVisitor;

		RouterFunctionVisitor(PredicateVisitor predicateVisitor) {
			this.predicateVisitor = predicateVisitor;
		}

		@Override
		public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
			predicate.accept(predicateVisitor);
		}

		@Override
		public void attributes(Map<String, Object> attributes) {
			this.predicateVisitor.attributesRef.set(attributes);
		}

		@Override
		public void startNested(RequestPredicate predicate) {

		}

		@Override
		public void endNested(RequestPredicate predicate) {

		}

		@Override
		public void resources(Function<ServerRequest, Optional<Resource>> lookupFunction) {

		}

		@Override
		public void unknown(RouterFunction<?> routerFunction) {
		}

	}

	class PredicateVisitor implements RequestPredicates.Visitor {

		private final AtomicReference<Map<String, Object>> attributesRef = new AtomicReference<>();

		private ArgumentSupplier argumentSupplier;

		private Map<String, Object> attributes;

		@Override
		public void unknown(RequestPredicate predicate) {
			if (predicate instanceof ArgumentSupplier argumentSupplier) {
				this.argumentSupplier = argumentSupplier;
				this.attributes = attributesRef.get();
			}
		}

		@Override
		public void method(Set<HttpMethod> methods) {

		}

		@Override
		public void path(String pattern) {

		}

		@Override
		public void pathExtension(String extension) {

		}

		@Override
		public void header(String name, String value) {

		}

		@Override
		public void param(String name, String value) {

		}

		@Override
		public void startAnd() {

		}

		@Override
		public void and() {

		}

		@Override
		public void endAnd() {

		}

		@Override
		public void startOr() {

		}

		@Override
		public void or() {

		}

		@Override
		public void endOr() {

		}

		@Override
		public void startNegate() {

		}

		@Override
		public void endNegate() {

		}

	}

}
