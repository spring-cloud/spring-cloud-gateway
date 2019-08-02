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

import java.util.Map;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.core.convert.ConversionService;
import org.springframework.validation.Validator;

@Deprecated
public abstract class ConfigurationUtils {

	@Deprecated
	public static void bind(Object o, Map<String, Object> properties,
			String configurationPropertyName, String bindingName, Validator validator) {
		bind(o, properties, configurationPropertyName, bindingName, validator, null);
	}

	@Deprecated
	public static void bind(Object o, Map<String, Object> properties,
			String configurationPropertyName, String bindingName, Validator validator,
			ConversionService conversionService) {
		Object toBind = getTargetObject(o);
		Bindable<?> bindable = Bindable.ofInstance(toBind);
		ConfigurationService.bindOrCreate(bindable, properties,
				configurationPropertyName/* , bindingName */, validator,
				conversionService);
	}

	@Deprecated
	@SuppressWarnings("unchecked")
	public static <T> T getTargetObject(Object candidate) {
		return ConfigurationService.getTargetObject(candidate);
	}

}
