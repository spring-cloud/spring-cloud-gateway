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

package org.springframework.cloud.gateway.rsocket.client;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.rsocket.RSocketRequester;

/**
 * Automatically subscribes to {@link BrokerClient}.
 */
public class BrokerClientConnectionListener
		implements ApplicationListener<ApplicationReadyEvent>, Ordered {

	private final BrokerClient brokerClient;

	private final ApplicationEventPublisher publisher;

	public BrokerClientConnectionListener(BrokerClient brokerClient,
			ApplicationEventPublisher publisher) {
		this.brokerClient = brokerClient;
		this.publisher = publisher;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		// TODO: is there a better event the just RSocketRequester?
		// TODO: save Disposable?
		this.brokerClient.connect()
				.subscribe(requester -> publisher
						.publishEvent(new RSocketRequesterEvent<>(
								BrokerClientConnectionListener.this, requester)));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE; // TODO: configurable
	}

	private static final class RSocketRequesterEvent<T extends RSocketRequester>
			extends PayloadApplicationEvent<T> {

		private RSocketRequesterEvent(Object source, T payload) {
			super(source, payload);
		}

		@Override
		public ResolvableType getResolvableType() {
			return ResolvableType.forClassWithGenerics(getClass(),
					ResolvableType.forClass(RSocketRequester.class));
		}

	}

}
