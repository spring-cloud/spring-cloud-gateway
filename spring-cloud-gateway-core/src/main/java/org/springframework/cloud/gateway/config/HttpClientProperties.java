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
import reactor.ipc.netty.resources.PoolResources;

/**
 * Configuration properties for the Netty {@link reactor.ipc.netty.http.client.HttpClient}
 */
@ConfigurationProperties("spring.cloud.gateway.httpclient")
public class HttpClientProperties {

	/** Pool configuration for Netty HttpClient */
	private Pool pool = new Pool();

	/** Proxy configuration for Netty HttpClient */
	private Proxy proxy = new Proxy();

	/** SSL configuration for Netty HttpClient */
	private Ssl ssl = new Ssl();

	public Pool getPool() {
		return pool;
	}

	public void setPool(Pool pool) {
		this.pool = pool;
	}

	public Proxy getProxy() {
		return proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	public Ssl getSsl() {
		return ssl;
	}

	public void setSsl(Ssl ssl) {
		this.ssl = ssl;
	}

	public static class Pool {

		public enum PoolType { ELASTIC, FIXED, DISABLED }

		/** Type of pool for HttpClient to use, defaults to ELASTIC. */
		private PoolType type = PoolType.ELASTIC;

		/** The channel pool map name, defaults to proxy. */
		private String name = "proxy";

		/** Only for type FIXED, the maximum number of connections before starting pending acquisition on existing ones. */
		private Integer maxConnections = PoolResources.DEFAULT_POOL_MAX_CONNECTION;

		/** Only for type FIXED, the maximum time in millis to wait for aquiring. */
		private Long acquireTimeout = PoolResources.DEFAULT_POOL_ACQUIRE_TIMEOUT;

		public PoolType getType() {
			return type;
		}

		public void setType(PoolType type) {
			this.type = type;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getMaxConnections() {
			return maxConnections;
		}

		public void setMaxConnections(Integer maxConnections) {
			this.maxConnections = maxConnections;
		}

		public Long getAcquireTimeout() {
			return acquireTimeout;
		}

		public void setAcquireTimeout(Long acquireTimeout) {
			this.acquireTimeout = acquireTimeout;
		}

		@Override
		public String toString() {
			return "Pool{" +
					"type=" + type +
					", name='" + name + '\'' +
					", maxConnections=" + maxConnections +
					", acquireTimeout=" + acquireTimeout +
					'}';
		}
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

	public class Ssl {
		/** Installs the netty InsecureTrustManagerFactory. This is insecure and not suitable for production. */
		private boolean useInsecureTrustManager = false;

		//TODO: support configuration of other trust manager factories

		public boolean isUseInsecureTrustManager() {
			return useInsecureTrustManager;
		}

		public void setUseInsecureTrustManager(boolean useInsecureTrustManager) {
			this.useInsecureTrustManager = useInsecureTrustManager;
		}

		@Override
		public String toString() {
			return "Ssl{" +
					"useInsecureTrustManager=" + useInsecureTrustManager +
					'}';
		}
	}

	@Override
	public String toString() {
		return "HttpClientProperties{" +
				"pool=" + pool +
				", proxy=" + proxy +
				'}';
	}
}
