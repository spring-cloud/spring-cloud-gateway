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

package org.springframework.cloud.gateway.route;

import org.junit.Test;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.event.ParentHeartbeatEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class RouteRefreshListenerTests {

	@Test
	public void onInstanceRegisteredEvent() {
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		RouteRefreshListener listener = new RouteRefreshListener(publisher);

		listener.onApplicationEvent(new InstanceRegisteredEvent<>(this, new Object()));

		verify(publisher).publishEvent(any(RefreshRoutesEvent.class));
	}

	@Test
	public void onHeartbeatEvent() {
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		RouteRefreshListener listener = new RouteRefreshListener(publisher);

		listener.onApplicationEvent(new HeartbeatEvent(this, 1L));
		listener.onApplicationEvent(new HeartbeatEvent(this, 1L));
		listener.onApplicationEvent(new HeartbeatEvent(this, 2L));

		verify(publisher, times(2)).publishEvent(any(RefreshRoutesEvent.class));
	}

	@Test
	public void onParentHeartbeatEvent() {
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		RouteRefreshListener listener = new RouteRefreshListener(publisher);

		listener.onApplicationEvent(new ParentHeartbeatEvent(this, 1L));
		listener.onApplicationEvent(new ParentHeartbeatEvent(this, 1L));
		listener.onApplicationEvent(new ParentHeartbeatEvent(this, 2L));

		verify(publisher, times(2)).publishEvent(any(RefreshRoutesEvent.class));
	}
}
