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

import java.util.function.Function;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.core.style.DefaultToStringStyler;
import org.springframework.core.style.DefaultValueStyler;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.ClassUtils;

public class GatewayToStringStyler extends DefaultToStringStyler {

	private static final GatewayToStringStyler FILTER_INSTANCE = new GatewayToStringStyler(GatewayFilterFactory.class,
			NameUtils::normalizeFilterFactoryName);

	private final Function<Class, String> classNameFormatter;

	private final Class instanceClass;

	public static ToStringCreator filterToStringCreator(Object obj) {
		return new ToStringCreator(obj, FILTER_INSTANCE);
	}

	public GatewayToStringStyler(Class instanceClass, Function<Class, String> classNameFormatter) {
		super(new DefaultValueStyler());
		this.classNameFormatter = classNameFormatter;
		this.instanceClass = instanceClass;
	}

	@Override
	public void styleStart(StringBuilder buffer, Object obj) {
		if (!obj.getClass().isArray()) {
			String shortName;
			if (instanceClass.isInstance(obj)) {
				shortName = classNameFormatter.apply(obj.getClass());
			}
			else {
				shortName = ClassUtils.getShortName(obj.getClass());
			}
			buffer.append('[').append(shortName);
		}
		else {
			buffer.append('[');
			styleValue(buffer, obj);
		}
	}

}
