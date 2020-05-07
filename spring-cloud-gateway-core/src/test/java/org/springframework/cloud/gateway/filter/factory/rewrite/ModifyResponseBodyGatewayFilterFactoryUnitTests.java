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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.junit.Test;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory.Config;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;

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
		assertThat(filter.toString()).contains("String").contains("Integer")
				.contains("mycontenttype");
	}

}
