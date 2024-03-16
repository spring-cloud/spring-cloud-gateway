/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.Map;

import jakarta.validation.constraints.Max;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationServiceTests {

	@Test
	public void validationOnCreateWorks() {
		Map<String, Object> map = Collections.singletonMap("config.value", 11);

		Assertions.assertThrows(BindException.class, () -> ConfigurationService
				.bindOrCreate(Bindable.of(ValidatedConfig.class), map, "config", getValidator(), null));
	}

	@Test
	public void createWorks() {
		Map<String, Object> map = Collections.singletonMap("config.value", 9);

		ValidatedConfig config = ConfigurationService.bindOrCreate(Bindable.of(ValidatedConfig.class), map, "config",
				getValidator(), null);

		assertThat(config).isNotNull().extracting(ValidatedConfig::getValue).isEqualTo(9);
	}

	@Test
	public void validationOnBindWorks() {
		Map<String, Object> map = Collections.singletonMap("config.value", 11);

		ValidatedConfig config = new ValidatedConfig();

		Assertions.assertThrows(BindException.class, () -> ConfigurationService
				.bindOrCreate(Bindable.ofInstance(config), map, "config", getValidator(), null));

	}

	@Test
	public void bindWorks() {
		Map<String, Object> map = Collections.singletonMap("config.value", 9);

		ValidatedConfig config = new ValidatedConfig();
		ConfigurationService.bindOrCreate(Bindable.ofInstance(config), map, "config", getValidator(), null);

		assertThat(config).isNotNull().extracting(ValidatedConfig::getValue).isEqualTo(9);
	}

	LocalValidatorFactoryBean getValidator() {
		GenericApplicationContext context = new GenericApplicationContext();
		context.refresh();
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.setApplicationContext(context);
		validator.setMessageInterpolator(new MessageInterpolatorFactory().getObject());
		validator.afterPropertiesSet();
		return validator;
	}

	public static class ValidatedConfig {

		@Max(10)
		private int value;

		public int getValue() {
			return this.value;
		}

		public void setValue(int value) {
			this.value = value;
		}

	}

}
