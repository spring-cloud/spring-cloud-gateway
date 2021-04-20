/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

public class MvcFoundOnClasspathFailureAnalyzer
		extends AbstractFailureAnalyzer<MvcFoundOnClasspathException> {

	/**
	 * Message for MvcFoundOnClasspathException.
	 */
	public static final String MESSAGE = "Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway.";

	/**
	 * Action for MvcFoundOnClasspathException.
	 */
	public static final String ACTION = "Please set spring.main.web-application-type=reactive or remove spring-boot-starter-web dependency.";

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure,
			MvcFoundOnClasspathException cause) {
		return new FailureAnalysis(MESSAGE, ACTION, cause);
	}

}
