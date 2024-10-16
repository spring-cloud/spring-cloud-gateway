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

package org.springframework.cloud.gateway.server.mvc.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;

@ConfigurationProperties(XForwardedRequestHeadersFilterProperties.PREFIX)
public class XForwardedRequestHeadersFilterProperties {

	/**
	 * XForwardedRequestHeadersFilterProperties prefix.
	 */
	public static final String PREFIX = GatewayMvcProperties.PREFIX + ".x-forwarded-request-headers-filter";

	/** The order of the XForwardedHeadersFilter. */
	private int order = 0;

	/** If the XForwardedHeadersFilter is enabled. */
	private boolean enabled = true;

	/** If X-Forwarded-For is enabled. */
	private boolean forEnabled = true;

	/** If X-Forwarded-Host is enabled. */
	private boolean hostEnabled = true;

	/** If X-Forwarded-Port is enabled. */
	private boolean portEnabled = true;

	/** If X-Forwarded-Proto is enabled. */
	private boolean protoEnabled = true;

	/** If X-Forwarded-Prefix is enabled. */
	private boolean prefixEnabled = true;

	/** If appending X-Forwarded-For as a list is enabled. */
	private boolean forAppend = true;

	/** If appending X-Forwarded-Host as a list is enabled. */
	private boolean hostAppend = true;

	/** If appending X-Forwarded-Port as a list is enabled. */
	private boolean portAppend = true;

	/** If appending X-Forwarded-Proto as a list is enabled. */
	private boolean protoAppend = true;

	/** If appending X-Forwarded-Prefix as a list is enabled. */
	private boolean prefixAppend = true;

	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isForEnabled() {
		return forEnabled;
	}

	public void setForEnabled(boolean forEnabled) {
		this.forEnabled = forEnabled;
	}

	public boolean isHostEnabled() {
		return hostEnabled;
	}

	public void setHostEnabled(boolean hostEnabled) {
		this.hostEnabled = hostEnabled;
	}

	public boolean isPortEnabled() {
		return portEnabled;
	}

	public void setPortEnabled(boolean portEnabled) {
		this.portEnabled = portEnabled;
	}

	public boolean isProtoEnabled() {
		return protoEnabled;
	}

	public void setProtoEnabled(boolean protoEnabled) {
		this.protoEnabled = protoEnabled;
	}

	public boolean isPrefixEnabled() {
		return prefixEnabled;
	}

	public void setPrefixEnabled(boolean prefixEnabled) {
		this.prefixEnabled = prefixEnabled;
	}

	public boolean isForAppend() {
		return forAppend;
	}

	public void setForAppend(boolean forAppend) {
		this.forAppend = forAppend;
	}

	public boolean isHostAppend() {
		return hostAppend;
	}

	public void setHostAppend(boolean hostAppend) {
		this.hostAppend = hostAppend;
	}

	public boolean isPortAppend() {
		return portAppend;
	}

	public void setPortAppend(boolean portAppend) {
		this.portAppend = portAppend;
	}

	public boolean isProtoAppend() {
		return protoAppend;
	}

	public void setProtoAppend(boolean protoAppend) {
		this.protoAppend = protoAppend;
	}

	public boolean isPrefixAppend() {
		return prefixAppend;
	}

	public void setPrefixAppend(boolean prefixAppend) {
		this.prefixAppend = prefixAppend;
	}

}
