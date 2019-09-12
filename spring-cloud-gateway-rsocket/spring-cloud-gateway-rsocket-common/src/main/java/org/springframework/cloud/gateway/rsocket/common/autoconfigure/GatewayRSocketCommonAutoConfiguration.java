/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.common.autoconfigure;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.function.Supplier;

import io.rsocket.RSocket;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass(RSocket.class)
@AutoConfigureBefore(RSocketStrategiesAutoConfiguration.class)
public class GatewayRSocketCommonAutoConfiguration {

	/**
	 * Name of id generator bean.
	 */
	public static final String ID_GENERATOR_BEAN_NAME = "gatewayRSocketIdGenerator";

	private final SecureRandom secureRandom = new SecureRandom();

	@Bean
	public RSocketStrategiesCustomizer gatewayRSocketStrategiesCustomizer() {
		return strategies -> {
			strategies.decoder(new Forwarding.Decoder(), new RouteSetup.Decoder())
					.encoder(new Forwarding.Encoder(), new RouteSetup.Encoder());
		};
	}

	@Bean(name = ID_GENERATOR_BEAN_NAME)
	@ConditionalOnMissingBean(name = ID_GENERATOR_BEAN_NAME)
	public Supplier<BigInteger> gatewayRSocketIdGenerator() {
		return () -> {
			byte[] bytes = new byte[16];
			secureRandom.nextBytes(bytes);
			return new BigInteger(bytes);
		};
	}

}
