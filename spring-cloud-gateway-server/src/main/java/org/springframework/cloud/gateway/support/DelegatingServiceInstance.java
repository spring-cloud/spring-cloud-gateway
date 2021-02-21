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

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;

/**
 * A {@link ServiceInstance} implementation that uses a delegate instance under the hood.
 *
 * @author Spencer Gibb
 */
public class DelegatingServiceInstance implements ServiceInstance {

	final ServiceInstance delegate;

	private String overrideScheme;

	public DelegatingServiceInstance(ServiceInstance delegate, String overrideScheme) {
		this.delegate = delegate;
		this.overrideScheme = overrideScheme;
	}

	@Override
	public String getServiceId() {
		return delegate.getServiceId();
	}

	@Override
	public String getHost() {
		return delegate.getHost();
	}

	@Override
	public int getPort() {
		return delegate.getPort();
	}

	@Override
	public boolean isSecure() {
		// TODO: move to map
		if ("https".equals(this.overrideScheme) || "wss".equals(this.overrideScheme)) {
			return true;
		}
		return delegate.isSecure();
	}

	@Override
	public URI getUri() {
		return delegate.getUri();
	}

	@Override
	public Map<String, String> getMetadata() {
		return delegate.getMetadata();
	}

	@Override
	public String getScheme() {
		String scheme = delegate.getScheme();
		if (scheme != null) {
			return scheme;
		}
		return this.overrideScheme;
	}

}
