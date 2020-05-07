/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;
import org.springframework.validation.Validator;

public class ConfigurationService implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	private BeanFactory beanFactory;

	private Supplier<ConversionService> conversionService;

	private SpelExpressionParser parser = new SpelExpressionParser();

	private Supplier<Validator> validator;

	public ConfigurationService(BeanFactory beanFactory,
			ObjectProvider<ConversionService> conversionService,
			ObjectProvider<Validator> validator) {
		this.beanFactory = beanFactory;
		this.conversionService = conversionService::getIfAvailable;
		this.validator = validator::getIfAvailable;
	}

	public ConfigurationService(BeanFactory beanFactory,
			Supplier<ConversionService> conversionService,
			Supplier<Validator> validator) {
		this.beanFactory = beanFactory;
		this.conversionService = conversionService;
		this.validator = validator;
	}

	public ApplicationEventPublisher getPublisher() {
		return this.publisher;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public <T, C extends Configurable<T> & ShortcutConfigurable> ConfigurableBuilder<T, C> with(
			C configurable) {
		return new ConfigurableBuilder<T, C>(this, configurable);
	}

	public <T> InstanceBuilder<T> with(T instance) {
		return new InstanceBuilder<T>(this, instance);
	}

	/* for testing */ static <T> T bindOrCreate(Bindable<T> bindable,
			Map<String, Object> properties, String configurationPropertyName,
			Validator validator, ConversionService conversionService) {
		// see ConfigurationPropertiesBinder from spring boot for this definition.
		BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();

		if (validator != null) { // TODO: list of validators?
			handler = new ValidationBindHandler(handler, validator);
		}

		List<ConfigurationPropertySource> propertySources = Collections
				.singletonList(new MapConfigurationPropertySource(properties));

		return new Binder(propertySources, null, conversionService)
				.bindOrCreate(configurationPropertyName, bindable, handler);
	}

	@SuppressWarnings("unchecked")
	/* for testing */ static <T> T getTargetObject(Object candidate) {
		try {
			if (AopUtils.isAopProxy(candidate) && (candidate instanceof Advised)) {
				return (T) ((Advised) candidate).getTargetSource().getTarget();
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to unwrap proxied object", ex);
		}
		return (T) candidate;
	}

	public class ConfigurableBuilder<T, C extends Configurable<T> & ShortcutConfigurable>
			extends AbstractBuilder<T, ConfigurableBuilder<T, C>> {

		private final C configurable;

		public ConfigurableBuilder(ConfigurationService service, C configurable) {
			super(service);
			this.configurable = configurable;
		}

		@Override
		protected ConfigurableBuilder<T, C> getThis() {
			return this;
		}

		@Override
		protected void validate() {
			Assert.notNull(this.configurable, "configurable may not be null");
		}

		@Override
		protected Map<String, Object> normalizeProperties() {
			if (this.service.beanFactory != null) {
				return this.configurable.shortcutType().normalize(this.properties,
						this.configurable, this.service.parser, this.service.beanFactory);
			}
			return super.normalizeProperties();
		}

		@Override
		protected T doBind() {
			Bindable<T> bindable = Bindable.of(this.configurable.getConfigClass());
			T bound = bindOrCreate(bindable, this.normalizedProperties,
					this.configurable.shortcutFieldPrefix(),
					/* this.name, */this.service.validator.get(),
					this.service.conversionService.get());

			return bound;
		}

	}

	public class InstanceBuilder<T> extends AbstractBuilder<T, InstanceBuilder<T>> {

		private final T instance;

		public InstanceBuilder(ConfigurationService service, T instance) {
			super(service);
			this.instance = instance;
		}

		@Override
		protected InstanceBuilder<T> getThis() {
			return this;
		}

		@Override
		protected void validate() {
			Assert.notNull(this.instance, "instance may not be null");
		}

		@Override
		protected T doBind() {
			T toBind = getTargetObject(this.instance);
			Bindable<T> bindable = Bindable.ofInstance(toBind);
			return bindOrCreate(bindable, this.normalizedProperties, this.name,
					this.service.validator.get(), this.service.conversionService.get());
		}

	}

	public abstract class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {

		protected final ConfigurationService service;

		protected BiFunction<T, Map<String, Object>, ApplicationEvent> eventFunction;

		protected String name;

		protected Map<String, Object> normalizedProperties;

		protected Map<String, String> properties;

		public AbstractBuilder(ConfigurationService service) {
			this.service = service;
		}

		protected abstract B getThis();

		public B name(String name) {
			this.name = name;
			return getThis();
		}

		public B eventFunction(
				BiFunction<T, Map<String, Object>, ApplicationEvent> eventFunction) {
			this.eventFunction = eventFunction;
			return getThis();
		}

		public B normalizedProperties(Map<String, Object> normalizedProperties) {
			this.normalizedProperties = normalizedProperties;
			return getThis();
		}

		public B properties(Map<String, String> properties) {
			this.properties = properties;
			return getThis();
		}

		protected abstract void validate();

		protected Map<String, Object> normalizeProperties() {
			Map<String, Object> normalizedProperties = new HashMap<>();
			this.properties.forEach(normalizedProperties::put);
			return normalizedProperties;
		}

		protected abstract T doBind();

		public T bind() {
			validate();
			Assert.hasText(this.name, "name may not be empty");
			Assert.isTrue(this.properties != null || this.normalizedProperties != null,
					"properties and normalizedProperties both may not be null");

			if (this.normalizedProperties == null) {
				this.normalizedProperties = normalizeProperties();
			}

			T bound = doBind();

			if (this.eventFunction != null && this.service.publisher != null) {
				ApplicationEvent applicationEvent = this.eventFunction.apply(bound,
						this.normalizedProperties);
				this.service.publisher.publishEvent(applicationEvent);
			}

			return bound;
		}

	}

}
