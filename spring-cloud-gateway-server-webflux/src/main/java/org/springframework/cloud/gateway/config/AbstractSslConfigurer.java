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

import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import io.netty.handler.ssl.SslContextBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.util.ResourceUtils;

/**
 * Base class to configure SSL for component T. Returns an instance S with the resulting
 * configuration (can be the same as T).
 *
 * @author Abel Salgado Romero
 * @author Dominic Niemann
 */
public abstract class AbstractSslConfigurer<T, S> {

	protected final Log logger = LogFactory.getLog(this.getClass());

	private final HttpClientProperties.Ssl ssl;

	private final SslBundles bundles;

	protected AbstractSslConfigurer(HttpClientProperties.Ssl sslProperties, SslBundles bundles) {
		this.ssl = sslProperties;
		this.bundles = bundles;
	}

	abstract public S configureSsl(T client) throws SSLException;

	protected HttpClientProperties.Ssl getSslProperties() {
		return ssl;
	}

	protected SslBundle getBundle() {
		if (ssl.getSslBundle() != null && ssl.getSslBundle().length() > 0
				&& bundles.getBundleNames().contains(ssl.getSslBundle())) {
			return bundles.getBundle(ssl.getSslBundle());
		}
		return null;
	}

	protected X509Certificate[] getTrustedX509CertificatesForTrustManager() {

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
				throw new RuntimeException("Could not load key store '" + ssl.getKeyStore() + "'", e);
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

}
