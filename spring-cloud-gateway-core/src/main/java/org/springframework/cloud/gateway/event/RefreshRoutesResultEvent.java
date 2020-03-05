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

package org.springframework.cloud.gateway.event;

import org.springframework.cloud.gateway.route.CachingRouteLocator;
import org.springframework.context.ApplicationEvent;

/**
 * @author alvin
 */
public class RefreshRoutesResultEvent extends ApplicationEvent {

	private Throwable throwable;

	private RefreshRoutesResult result;

	public RefreshRoutesResultEvent(Object source, Throwable throwable) {
		super(source);
		this.throwable = throwable;
		result = RefreshRoutesResult.ERROR;
	}

	public RefreshRoutesResultEvent(Object source) {
		super(source);
		result = RefreshRoutesResult.SUCCESS;
	}

	public Throwable getThrowable() {
		return throwable;
	}


	public enum RefreshRoutesResult {
		/**
		 * SUCCESS means when {@link CachingRouteLocator} refresh successful.
		 */
		SUCCESS,
		/**
		 * fail means when {@link CachingRouteLocator} refresh failed.
		 */
		ERROR
	}

	public RefreshRoutesResult getResult() {
		return result;
	}

}
