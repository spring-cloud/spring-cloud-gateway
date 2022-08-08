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

import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import org.springframework.util.ResourceUtils;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * @author Alberto C. Ríos
 */
public class GRPCSSLContextFactory {

	private final HttpClientProperties.Ssl ssl;

	public GRPCSSLContextFactory(HttpClientProperties properties) {
		ssl = properties.getSsl();
	}

	public SslContext getSslContext() throws SSLException {

		SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();

		if (ssl.isUseInsecureTrustManager()) {
			sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers()[0]);
		}

		if (ssl.isUseInsecureTrustManager() == false
				&& ssl.getTrustedX509Certificates().size() > 0) {
			sslContextBuilder.trustManager(ssl.getTrustedX509CertificatesForTrustManager());
		}

		return sslContextBuilder
				// TODO: is this necessary?
				// .keyManager(getKeyManagerFactory())
				.build();
	}

	protected KeyManagerFactory getKeyManagerFactory() {
		try {
			if (ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0) {
				KeyManagerFactory keyManagerFactory = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				char[] keyPassword = ssl.getKeyPassword() != null ? ssl.getKeyPassword().toCharArray() : null;

				if (keyPassword == null && ssl.getKeyStorePassword() != null) {
					keyPassword = ssl.getKeyStorePassword().toCharArray();
				}

				keyManagerFactory.init(this.createKeyStore(ssl), keyPassword);

				return keyManagerFactory;
			}

			return null;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	protected KeyStore createKeyStore(HttpClientProperties.Ssl ssl) {
//		HttpClientProperties.Ssl ssl = properties.getSsl();
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
}
