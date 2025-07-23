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

package org.springframework.cloud.gateway.support.tagsprovider;

import java.util.function.Function;

import io.micrometer.core.instrument.Tags;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Ingyu Hwang
 */
public interface GatewayTagsProvider extends Function<ServerWebExchange, Tags> {

	default GatewayTagsProvider and(GatewayTagsProvider other) {
		Assert.notNull(other, "other must not be null");

		return exchange -> other.apply(exchange).and(apply(exchange));
	}

}
