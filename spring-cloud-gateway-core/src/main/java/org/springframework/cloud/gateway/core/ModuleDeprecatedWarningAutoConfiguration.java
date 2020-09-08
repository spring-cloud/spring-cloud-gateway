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

package org.springframework.cloud.gateway.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModuleDeprecatedWarningAutoConfiguration {

	private static final Log log = LogFactory
			.getLog(ModuleDeprecatedWarningAutoConfiguration.class);

	public ModuleDeprecatedWarningAutoConfiguration() {
		if (log.isWarnEnabled()) {
			log.warn(
					"The spring-cloud-gateway-core module had been deprecated in favor of spring-cloud-gateway-server");
		}
	}

}
