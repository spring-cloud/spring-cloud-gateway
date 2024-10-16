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

package org.springframework.cloud.gateway.actuate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Marta Medio
 */
class GatewayEndpointInfo {

	private String href;

	private List<String> methods;

	public String getHref() {
		return href;
	}

	public void setHref(String href) {
		this.href = href;
	}

	public String[] getMethods() {
		return methods.stream().toArray(String[]::new);
	}

	GatewayEndpointInfo(String href, String method) {
		this.href = href;
		this.methods = Collections.singletonList(method);
	}

	GatewayEndpointInfo(String href, List<String> methods) {
		this.href = href;
		this.methods = methods;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		GatewayEndpointInfo that = (GatewayEndpointInfo) o;
		return Objects.equals(href, that.href) && Objects.equals(methods, that.methods);
	}

	@Override
	public int hashCode() {
		return Objects.hash(href, methods);
	}

}
