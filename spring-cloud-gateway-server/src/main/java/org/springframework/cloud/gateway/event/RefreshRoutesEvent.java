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

package org.springframework.cloud.gateway.event;

import java.util.Map;

import org.springframework.context.ApplicationEvent;
import org.springframework.util.CollectionUtils;

/**
 * @author Spencer Gibb
 */
public class RefreshRoutesEvent extends ApplicationEvent {

	private final Map<String, Object> metadata;

	/**
	 * Create a new ApplicationEvent.
	 * @param source the object on which the event initially occurred (never {@code null})
	 */
	public RefreshRoutesEvent(Object source) {
		this(source, null);
	}

	/**
	 * Create a new ApplicationEvent that should refresh filtering by {@link #metadata}.
	 * @param source the object on which the event initially occurred (never {@code null})
	 * @param metadata map of metadata the routes should match ({code null} is considered
	 * a global refresh)
	 */
	public RefreshRoutesEvent(Object source, Map<String, Object> metadata) {
		super(source);
		this.metadata = metadata;
	}

	public boolean isScoped() {
		return !CollectionUtils.isEmpty(getMetadata());
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

}
