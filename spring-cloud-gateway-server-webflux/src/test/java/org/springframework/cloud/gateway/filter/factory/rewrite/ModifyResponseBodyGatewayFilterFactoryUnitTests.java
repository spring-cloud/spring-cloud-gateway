/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory.Config;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

public class ModifyResponseBodyGatewayFilterFactoryUnitTests {

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setInClass(String.class);
		config.setOutClass(Integer.class);
		config.setNewContentType("mycontenttype");
		GatewayFilter filter = new ModifyResponseBodyGatewayFilterFactory(
				new DefaultServerCodecConfigurer().getReaders(), emptySet(), emptySet())
			.apply(config);
		assertThat(filter.toString()).contains("String").contains("Integer").contains("mycontenttype");
	}

	@Test
	public void configShouldAllowExchangeStrategiesConfiguration() {
		// Create custom ExchangeStrategies with larger buffer size
		ExchangeStrategies customStrategies = ExchangeStrategies.builder()
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
			.build();

		Config config = new Config();
		config.setInClass(String.class);
		config.setOutClass(String.class);
		config.setExchangeStrategies(customStrategies);

		// Verify that the ExchangeStrategies can be set and retrieved
		assertThat(config.getExchangeStrategies()).isNotNull();
		assertThat(config.getExchangeStrategies()).isEqualTo(customStrategies);
	}

	@Test
	public void configShouldHaveNullExchangeStrategiesByDefault() {
		Config config = new Config();
		config.setInClass(String.class);
		config.setOutClass(String.class);

		// Verify that ExchangeStrategies is null by default
		assertThat(config.getExchangeStrategies()).isNull();
	}

}
