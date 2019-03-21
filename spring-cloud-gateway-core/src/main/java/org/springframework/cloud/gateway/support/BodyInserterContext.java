/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.support;

import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BodyInserterContext implements BodyInserter.Context {
	private final ExchangeStrategies exchangeStrategies;

	public BodyInserterContext() {
		this.exchangeStrategies = ExchangeStrategies.withDefaults();
	}

	public BodyInserterContext(ExchangeStrategies exchangeStrategies) {
		this.exchangeStrategies = exchangeStrategies; //TODO: support custom strategies
	}

	@Override
	public List<HttpMessageWriter<?>> messageWriters() {
		return exchangeStrategies.messageWriters();
	}
	@Override
	public Optional<ServerHttpRequest> serverRequest() {
		return Optional.empty();
	}
	@Override
	public Map<String, Object> hints() {
		return Collections.emptyMap(); //TODO: support hints
	}
}
