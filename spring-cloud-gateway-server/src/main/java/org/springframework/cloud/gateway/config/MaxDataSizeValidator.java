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

package org.springframework.cloud.gateway.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Max;

import org.springframework.util.unit.DataSize;

// https://in.relation.to/2017/03/02/adding-custom-constraint-definitions-via-the-java-service-loader/
public class MaxDataSizeValidator implements ConstraintValidator<Max, DataSize> {

	private long maxValue;

	@Override
	public boolean isValid(DataSize value, ConstraintValidatorContext context) {
		// null values are valid
		if (value == null) {
			return true;
		}
		return value.toBytes() <= maxValue;
	}

	@Override
	public void initialize(Max maxValue) {
		this.maxValue = maxValue.value();
	}

}
