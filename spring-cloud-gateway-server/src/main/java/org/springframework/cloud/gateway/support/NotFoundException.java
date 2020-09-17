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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author Spencer Gibb
 */
public class NotFoundException extends ResponseStatusException {

	public NotFoundException(String message) {
		this(HttpStatus.SERVICE_UNAVAILABLE, message);
	}

	public NotFoundException(String message, Throwable cause) {
		this(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
	}

	private NotFoundException(HttpStatus httpStatus, String message) {
		super(httpStatus, message);
	}

	private NotFoundException(HttpStatus httpStatus, String message, Throwable cause) {
		super(httpStatus, message, cause);
	}

	public static NotFoundException create(boolean with404, String message) {
		HttpStatus httpStatus = with404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
		return new NotFoundException(httpStatus, message);
	}

	public static NotFoundException create(boolean with404, String message, Throwable cause) {
		HttpStatus httpStatus = with404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
		return new NotFoundException(httpStatus, message, cause);
	}

}
