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

package org.springframework.cloud.gateway.server.mvc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.fn;

@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.function.context.FunctionCatalog")
@ConditionalOnProperty(name = "spring.cloud.gateway.function.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultFunctionConfiguration {

	@Bean
	RouterFunction<ServerResponse> gatewayToFunctionRouter() {
		// @formatter:off
		return route("functionroute")
				.POST("/{path}/{name}", fn("{path}/{name}"))
				.POST("/{path}", fn("{path}"))
				.GET("/{path}/{name}", fn("{path}/{name}"))
				.GET("/{path}", fn("{path}"))
			.build();
		// @formatter:on
	}

}
