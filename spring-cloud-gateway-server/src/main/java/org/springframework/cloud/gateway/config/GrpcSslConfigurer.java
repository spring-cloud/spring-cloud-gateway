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

import javax.net.ssl.SSLException;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;

/**
 * @author Alberto C. Ríos
 * @author Dominic Niemann
 */
public class GrpcSslConfigurer extends AbstractSslConfigurer<NettyChannelBuilder, ManagedChannel> {

	public GrpcSslConfigurer(HttpClientProperties.Ssl sslProperties, SslBundles bundles) {
		super(sslProperties, bundles);
	}

	@Override
	public ManagedChannel configureSsl(NettyChannelBuilder NettyChannelBuilder) throws SSLException {
		return NettyChannelBuilder.useTransportSecurity().sslContext(getSslContext()).build();
	}

	private SslContext getSslContext() throws SSLException {

		final SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();

		final HttpClientProperties.Ssl ssl = getSslProperties();
		boolean useInsecureTrustManager = ssl.isUseInsecureTrustManager();
		SslBundle bundle = getBundle();
		if (useInsecureTrustManager) {
			sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers()[0]);
		}

		if (!useInsecureTrustManager && ssl.getTrustedX509Certificates().size() > 0) {
			sslContextBuilder.trustManager(getTrustedX509CertificatesForTrustManager());
		}
		else if (bundle != null) {
			sslContextBuilder.trustManager(bundle.getManagers().getTrustManagerFactory());
		}

		if (bundle != null) {
			sslContextBuilder.keyManager(bundle.getManagers().getKeyManagerFactory());
		}
		else {
			sslContextBuilder.keyManager(getKeyManagerFactory());
		}
		return sslContextBuilder.build();
	}

}
