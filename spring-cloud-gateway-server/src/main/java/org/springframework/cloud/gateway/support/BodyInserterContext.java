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

package org.springframework.cloud.gateway.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.gateway.util.MediaTypeUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

public class BodyInserterContext implements BodyInserter.Context {

	final static ExchangeStrategies SSE_EXCHANGE_STRATEGIES;

	private final ExchangeStrategies exchangeStrategies;

	static {
		SSE_EXCHANGE_STRATEGIES = ExchangeStrategies.builder()
				.codecs(c -> c.customCodecs().register(new ServerSentEventHttpMessageWriter(new Jackson2JsonEncoder())))
				.build();
	}

	public BodyInserterContext() {
		this.exchangeStrategies = ExchangeStrategies.withDefaults();
	}

	public BodyInserterContext(ExchangeStrategies exchangeStrategies) {
		this.exchangeStrategies = exchangeStrategies; // TODO: support custom strategies
	}

	public BodyInserterContext(MediaType mediaType, List<MediaType> streamingMediaTypes) {
		if (MediaTypeUtils.isStreamingMediaType(mediaType, streamingMediaTypes)) {
			// SSE codec should be on default ExchangeStrategies
			this.exchangeStrategies = SSE_EXCHANGE_STRATEGIES;
		}
		else {
			this.exchangeStrategies = ExchangeStrategies.withDefaults();
		}

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
		return Collections.emptyMap(); // TODO: support hints
	}

}
