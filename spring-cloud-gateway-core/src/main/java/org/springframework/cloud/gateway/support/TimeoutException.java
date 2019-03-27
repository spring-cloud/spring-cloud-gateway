/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.support;

import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = GATEWAY_TIMEOUT, reason = "Response took longer than configured timeout")
public class TimeoutException extends Exception {

	public TimeoutException() {
	}

	public TimeoutException(String message) {
		super(message);
	}

	/**
	 * Disables fillInStackTrace for performance reasons.
	 * @return
	 */
	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}
