/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfiguration(after = GatewayServerMvcAutoConfiguration.class)
public class TestAutoConfiguration {

	@Bean
	@Lazy // so env has the chance to get local.server.port
	public RestTestClient restTestClient(Environment env) {
		String port = env.getProperty("local.server.port", "8080");
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Bean
	public TestController testController() {
		return new TestController();
	}

}
