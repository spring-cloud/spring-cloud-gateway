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

package org.springframework.cloud.gateway.config;

import java.security.cert.X509Certificate;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;

public class HttpClientSslConfigurer extends AbstractSslConfigurer<HttpClient, HttpClient> {

	private final ServerProperties serverProperties;

	public HttpClientSslConfigurer(HttpClientProperties.Ssl sslProperties, ServerProperties serverProperties,
			SslBundles bundles) {
		super(sslProperties, bundles);
		this.serverProperties = serverProperties;
	}

	public HttpClient configureSsl(HttpClient client) {
		final HttpClientProperties.Ssl ssl = getSslProperties();

		if (getBundle() != null || (ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0)
				|| getTrustedX509CertificatesForTrustManager().length > 0 || ssl.isUseInsecureTrustManager()) {
			client = client.secure(sslContextSpec -> {
				// configure ssl
				configureSslContext(ssl, sslContextSpec);
			});
		}
		return client;
	}

	protected void configureSslContext(HttpClientProperties.Ssl ssl, SslProvider.SslContextSpec sslContextSpec) {
		SslProvider.ProtocolSslContextSpec clientSslContext = (serverProperties.getHttp2().isEnabled())
				? Http2SslContextSpec.forClient() : Http11SslContextSpec.forClient();
		clientSslContext.configure(sslContextBuilder -> {
			X509Certificate[] trustedX509Certificates = getTrustedX509CertificatesForTrustManager();
			SslBundle bundle = getBundle();
			if (trustedX509Certificates.length > 0) {
				setTrustManager(sslContextBuilder, trustedX509Certificates);
			}
			else if (ssl.isUseInsecureTrustManager()) {
				setTrustManager(sslContextBuilder, InsecureTrustManagerFactory.INSTANCE);
			}
			else if (bundle != null) {
				setTrustManager(sslContextBuilder, bundle.getManagers().getTrustManagerFactory());
			}

			try {
				if (bundle != null) {
					sslContextBuilder.keyManager(bundle.getManagers().getKeyManagerFactory());
				}
				else {
					sslContextBuilder.keyManager(getKeyManagerFactory());
				}
			}
			catch (Exception e) {
				logger.error(e);
			}
		});

		sslContextSpec.sslContext(clientSslContext)
			.handshakeTimeout(ssl.getHandshakeTimeout())
			.closeNotifyFlushTimeout(ssl.getCloseNotifyFlushTimeout())
			.closeNotifyReadTimeout(ssl.getCloseNotifyReadTimeout());
	}

}
