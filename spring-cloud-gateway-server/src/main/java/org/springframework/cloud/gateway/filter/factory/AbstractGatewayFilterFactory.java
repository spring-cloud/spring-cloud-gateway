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

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.support.AbstractConfigurable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * This class is BETA and may be subject to change in a future release.
 *
 * @param <C> {@link AbstractConfigurable} subtype
 */
public abstract class AbstractGatewayFilterFactory<C> extends AbstractConfigurable<C>
		implements GatewayFilterFactory<C>, ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	@SuppressWarnings("unchecked")
	public AbstractGatewayFilterFactory() {
		super((Class<C>) Object.class);
	}

	public AbstractGatewayFilterFactory(Class<C> configClass) {
		super(configClass);
	}

	protected ApplicationEventPublisher getPublisher() {
		return this.publisher;
	}

	protected void enableBodyCaching(String routeId) {
		if (routeId != null && getPublisher() != null) {
			// send an event to enable caching
			getPublisher().publishEvent(new EnableBodyCachingEvent(this, routeId));
		}
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public static class NameConfig {

		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
