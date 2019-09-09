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

import io.rsocket.RSocket;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.RSocketStrategies;

import static org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding.FORWARDING_MIME_TYPE;
import static org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup.ROUTE_SETUP_MIME_TYPE;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass(RSocket.class)
@AutoConfigureAfter({ GatewayRSocketCommonAutoConfiguration.class })
public class GatewayRSocketCommonMetadataAutoConfiguration implements InitializingBean {

	private final ApplicationContext context;

	public GatewayRSocketCommonMetadataAutoConfiguration(ApplicationContext context) {
		this.context = context;
	}

	@Override
	public void afterPropertiesSet() {
		RSocketStrategies rSocketStrategies = this.context
				.getBean(RSocketStrategies.class);
		MetadataExtractor metadataExtractor = rSocketStrategies.metadataExtractor();
		// TODO: see if possible to make easier in framework.
		if (metadataExtractor instanceof DefaultMetadataExtractor) {
			DefaultMetadataExtractor extractor = (DefaultMetadataExtractor) metadataExtractor;
			extractor.metadataToExtract(FORWARDING_MIME_TYPE, Forwarding.class,
					Forwarding.METADATA_KEY);
			extractor.metadataToExtract(ROUTE_SETUP_MIME_TYPE, RouteSetup.class,
					RouteSetup.METADATA_KEY);
		}
	}

}
