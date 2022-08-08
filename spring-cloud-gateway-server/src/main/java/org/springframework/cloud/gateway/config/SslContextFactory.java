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
import javax.net.ssl.TrustManagerFactory;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.util.ResourceUtils;

public abstract class SslContextFactory {

	protected final Log logger = LogFactory.getLog(this.getClass());

	private final HttpClientProperties.Ssl ssl;

	private final ServerProperties serverProperties;

	protected SslContextFactory(HttpClientProperties.Ssl sslProperties, ServerProperties serverProperties) {
		this.ssl = sslProperties;
		this.serverProperties = serverProperties;
	}

	protected HttpClient configureSsl(HttpClient httpClient) {

		if ((ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0)
				|| getTrustedX509CertificatesForTrustManager().length > 0 || ssl.isUseInsecureTrustManager()) {
			httpClient = httpClient.secure(sslContextSpec -> {
				// configure ssl
				configureSslContext(sslContextSpec);
			});
		}
		return httpClient;
	}

	protected void configureSslContext(SslProvider.SslContextSpec sslContextSpec) {
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
}
