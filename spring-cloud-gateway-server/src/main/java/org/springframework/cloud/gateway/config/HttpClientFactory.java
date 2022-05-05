/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpResponseDecoderSpec;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.DISABLED;
import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.FIXED;

/**
 * Factory Bean that allows users to extend and customize parts of the HttpClient. Also
 * allows for testing the configuration of the HttpClient.
 *
 * @author Spencer Gibb
 * @since 3.1.1
 */
public class HttpClientFactory extends AbstractFactoryBean<HttpClient> {

	protected final HttpClientProperties properties;

	protected final ServerProperties serverProperties;

	protected final List<HttpClientCustomizer> customizers;

	public HttpClientFactory(HttpClientProperties properties, ServerProperties serverProperties,
			List<HttpClientCustomizer> customizers) {
		this.properties = properties;
		this.serverProperties = serverProperties;
		this.customizers = customizers;
	}

	@Override
	public Class<?> getObjectType() {
		return HttpClient.class;
	}

	@Override
	protected HttpClient createInstance() {
		// configure pool resources
		ConnectionProvider connectionProvider = buildConnectionProvider(properties);

		HttpClient httpClient = HttpClient.create(connectionProvider)
				// TODO: move customizations to HttpClientCustomizers
				.httpResponseDecoder(this::httpResponseDecoder);

		if (serverProperties.getHttp2().isEnabled()) {
			httpClient = httpClient.protocol(HttpProtocol.HTTP11, HttpProtocol.H2);
		}

		if (properties.getConnectTimeout() != null) {
			httpClient = httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout());
		}

		httpClient = configureProxy(httpClient);

		httpClient = configureSsl(httpClient);

		if (properties.isWiretap()) {
			httpClient = httpClient.wiretap(true);
		}

		if (properties.isCompression()) {
			httpClient = httpClient.compress(true);
		}

		httpClient = applyCustomizers(httpClient);

		return httpClient;
	}

	private HttpClient applyCustomizers(HttpClient httpClient) {
		if (!CollectionUtils.isEmpty(customizers)) {
			customizers.sort(AnnotationAwareOrderComparator.INSTANCE);
			for (HttpClientCustomizer customizer : customizers) {
				httpClient = customizer.customize(httpClient);
			}
		}
		return httpClient;
	}

	protected HttpClient configureSsl(HttpClient httpClient) {
		HttpClientProperties.Ssl ssl = properties.getSsl();
		if ((ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0)
				|| getTrustedX509CertificatesForTrustManager().length > 0 || ssl.isUseInsecureTrustManager()) {
			httpClient = httpClient.secure(sslContextSpec -> {
				// configure ssl
				configureSslContext(ssl, sslContextSpec);
			});
		}
		return httpClient;
	}

	protected void configureSslContext(HttpClientProperties.Ssl ssl, SslProvider.SslContextSpec sslContextSpec) {
		SslProvider.ProtocolSslContextSpec clientSslContext = (serverProperties.getHttp2().isEnabled())
				? Http2SslContextSpec.forClient() : Http11SslContextSpec.forClient();
		clientSslContext.configure(sslContextBuilder -> {
			X509Certificate[] trustedX509Certificates = getTrustedX509CertificatesForTrustManager();
			if (trustedX509Certificates.length > 0) {
				setTrustManager(sslContextBuilder, trustedX509Certificates);
			}
			else if (ssl.isUseInsecureTrustManager()) {
				setTrustManager(sslContextBuilder, InsecureTrustManagerFactory.INSTANCE);
			}

			try {
				sslContextBuilder.keyManager(getKeyManagerFactory());
			}
			catch (Exception e) {
				logger.error(e);
			}
		});

		sslContextSpec.sslContext(clientSslContext).handshakeTimeout(ssl.getHandshakeTimeout())
				.closeNotifyFlushTimeout(ssl.getCloseNotifyFlushTimeout())
				.closeNotifyReadTimeout(ssl.getCloseNotifyReadTimeout());
	}

	protected HttpClient configureProxy(HttpClient httpClient) {
		// configure proxy if proxy host is set.
		if (StringUtils.hasText(properties.getProxy().getHost())) {
			HttpClientProperties.Proxy proxy = properties.getProxy();

			httpClient = httpClient.proxy(proxySpec -> {
				configureProxyProvider(proxy, proxySpec);
			});
		}
		return httpClient;
	}

	protected ProxyProvider.Builder configureProxyProvider(HttpClientProperties.Proxy proxy,
			ProxyProvider.TypeSpec proxySpec) {
		ProxyProvider.Builder builder = proxySpec.type(proxy.getType()).host(proxy.getHost());

		PropertyMapper map = PropertyMapper.get();

		map.from(proxy::getPort).whenNonNull().to(builder::port);
		map.from(proxy::getUsername).whenHasText().to(builder::username);
		map.from(proxy::getPassword).whenHasText().to(password -> builder.password(s -> password));
		map.from(proxy::getNonProxyHostsPattern).whenHasText().to(builder::nonProxyHosts);
		return builder;
	}

	protected HttpResponseDecoderSpec httpResponseDecoder(HttpResponseDecoderSpec spec) {
		if (properties.getMaxHeaderSize() != null) {
			// cast to int is ok, since @Max is Integer.MAX_VALUE
			spec.maxHeaderSize((int) properties.getMaxHeaderSize().toBytes());
		}
		if (properties.getMaxInitialLineLength() != null) {
			// cast to int is ok, since @Max is Integer.MAX_VALUE
			spec.maxInitialLineLength((int) properties.getMaxInitialLineLength().toBytes());
		}
		return spec;
	}

	protected X509Certificate[] getTrustedX509CertificatesForTrustManager() {
		HttpClientProperties.Ssl ssl = properties.getSsl();

		try {
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			ArrayList<Certificate> allCerts = new ArrayList<>();
			for (String trustedCert : ssl.getTrustedX509Certificates()) {
				try {
					URL url = ResourceUtils.getURL(trustedCert);
					Collection<? extends Certificate> certs = certificateFactory.generateCertificates(url.openStream());
					allCerts.addAll(certs);
				}
				catch (IOException e) {
					throw new RuntimeException("Could not load certificate '" + trustedCert + "'", e);
				}
			}
			return allCerts.toArray(new X509Certificate[allCerts.size()]);
		}
		catch (CertificateException e1) {
			throw new RuntimeException("Could not load CertificateFactory X.509", e1);
		}
	}

	protected KeyManagerFactory getKeyManagerFactory() {
		HttpClientProperties.Ssl ssl = properties.getSsl();
		try {
			if (ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0) {
				KeyManagerFactory keyManagerFactory = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				char[] keyPassword = ssl.getKeyPassword() != null ? ssl.getKeyPassword().toCharArray() : null;

				if (keyPassword == null && ssl.getKeyStorePassword() != null) {
					keyPassword = ssl.getKeyStorePassword().toCharArray();
				}

				keyManagerFactory.init(this.createKeyStore(), keyPassword);

				return keyManagerFactory;
			}

			return null;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	protected KeyStore createKeyStore() {
		HttpClientProperties.Ssl ssl = properties.getSsl();
		try {
			KeyStore store = ssl.getKeyStoreProvider() != null
					? KeyStore.getInstance(ssl.getKeyStoreType(), ssl.getKeyStoreProvider())
					: KeyStore.getInstance(ssl.getKeyStoreType());
			try {
				URL url = ResourceUtils.getURL(ssl.getKeyStore());
				store.load(url.openStream(),
						ssl.getKeyStorePassword() != null ? ssl.getKeyStorePassword().toCharArray() : null);
			}
			catch (Exception e) {
				throw new RuntimeException("Could not load key store ' " + ssl.getKeyStore() + "'", e);
			}

			return store;
		}
		catch (KeyStoreException | NoSuchProviderException e) {
			throw new RuntimeException("Could not load KeyStore for given type and provider", e);
		}
	}

	protected void setTrustManager(SslContextBuilder sslContextBuilder, X509Certificate... trustedX509Certificates) {
		sslContextBuilder.trustManager(trustedX509Certificates);
	}

	protected void setTrustManager(SslContextBuilder sslContextBuilder, TrustManagerFactory factory) {
		sslContextBuilder.trustManager(factory);
	}

	protected ConnectionProvider buildConnectionProvider(HttpClientProperties properties) {
		HttpClientProperties.Pool pool = properties.getPool();

		ConnectionProvider connectionProvider;
		if (pool.getType() == DISABLED) {
			connectionProvider = ConnectionProvider.newConnection();
		}
		else {
			// create either Fixed or Elastic pool
			ConnectionProvider.Builder builder = ConnectionProvider.builder(pool.getName());
			if (pool.getType() == FIXED) {
				builder.maxConnections(pool.getMaxConnections()).pendingAcquireMaxCount(-1)
						.pendingAcquireTimeout(Duration.ofMillis(pool.getAcquireTimeout()));
			}
			else {
				// Elastic
				builder.maxConnections(Integer.MAX_VALUE).pendingAcquireTimeout(Duration.ofMillis(0))
						.pendingAcquireMaxCount(-1);
			}

			if (pool.getMaxIdleTime() != null) {
				builder.maxIdleTime(pool.getMaxIdleTime());
			}
			if (pool.getMaxLifeTime() != null) {
				builder.maxLifeTime(pool.getMaxLifeTime());
			}
			builder.evictInBackground(pool.getEvictionInterval());
			builder.metrics(pool.isMetrics());
			connectionProvider = builder.build();
		}
		return connectionProvider;
	}

}
