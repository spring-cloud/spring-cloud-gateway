/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Netty {@link reactor.ipc.netty.http.client.HttpClient}
 */
@ConfigurationProperties("spring.cloud.gateway.httpclient")
public class HttpClientProperties {

	/** Proxy configuration for Netty HttpClient */
	private Proxy proxy = new Proxy();

	public Proxy getProxy() {
		return proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	public class Proxy {
		/** Hostname for proxy configuration of Netty HttpClient. */
		private String host;
		/** Port for proxy configuration of Netty HttpClient. */
		private Integer port;
		/** Username for proxy configuration of Netty HttpClient. */
		private String username;
		/** Password for proxy configuration of Netty HttpClient. */
		private String password;
		/** Regular expression (Java) for a configured list of hosts
		 * that should be reached directly, bypassing the proxy */
		private String nonProxyHostsPattern;

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public Integer getPort() {
			return port;
		}

		public void setPort(Integer port) {
			this.port = port;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getNonProxyHostsPattern() {
			return nonProxyHostsPattern;
		}

		public void setNonProxyHostsPattern(String nonProxyHostsPattern) {
			this.nonProxyHostsPattern = nonProxyHostsPattern;
		}

		@Override
		public String toString() {
			return "Proxy{" +
					"host='" + host + '\'' +
					", port=" + port +
					", username='" + username + '\'' +
					", password='" + password + '\'' +
					", nonProxyHostsPattern='" + nonProxyHostsPattern + '\'' +
					'}';
		}
	}
}
