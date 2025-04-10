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

package org.springframework.cloud.gateway.filter.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.config.KeyValue;
import org.springframework.cloud.gateway.support.config.KeyValueConfig;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * Adds one or more headers to the response’s headers without overriding previous values.
 * If the header is are already present, value(s) will not be set.
 *
 * @author jiangyuan
 */
public class AddResponseHeadersIfNotPresentGatewayFilterFactory extends AbstractGatewayFilterFactory<KeyValueConfig> {

	@Override
	public GatewayFilter apply(KeyValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange)
					.then(Mono.fromRunnable(() -> addResponseHeaderIfNotPresent(exchange, config)));
			}

			@Override
			public String toString() {
				ToStringCreator toStringCreator = filterToStringCreator(
						AddResponseHeadersIfNotPresentGatewayFilterFactory.this);
				for (KeyValue keyValue : config.getKeyValues()) {
					toStringCreator.append(keyValue.getKey(), keyValue.getValue());
				}
				return toStringCreator.toString();
			}
		};
	}

	private void addResponseHeaderIfNotPresent(ServerWebExchange exchange, KeyValueConfig config) {
		if (!exchange.getResponse().isCommitted()) {
			Map<String, List<String>> aggregatedHeaders = new HashMap<>();
			for (KeyValue keyValue : config.getKeyValues()) {
				String key = keyValue.getKey();
				List<String> candidateValue = aggregatedHeaders.get(key);
				if (candidateValue == null) {
					candidateValue = new ArrayList<>();
					candidateValue.add(keyValue.getValue());
					aggregatedHeaders.put(key, candidateValue);
				}
				else {
					candidateValue.add(keyValue.getValue());
				}
			}

			HttpHeaders headers = exchange.getResponse().getHeaders();
			for (Map.Entry<String, List<String>> kv : aggregatedHeaders.entrySet()) {
				String headerName = kv.getKey();

				boolean headerIsMissingOrBlank = headers.getOrEmpty(headerName)
					.stream()
					.allMatch(h -> !StringUtils.hasText(h));

				if (headerIsMissingOrBlank) {
					List<String> replacedValues = kv.getValue()
						.stream()
						.map(value -> ServerWebExchangeUtils.expand(exchange, value))
						.collect(Collectors.toList());
					headers.addAll(headerName, replacedValues);
				}
			}
		}
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("keyValues");
	}

	@Override
	public KeyValueConfig newConfig() {
		return new KeyValueConfig();
	}

	@Override
	public Class<KeyValueConfig> getConfigClass() {
		return KeyValueConfig.class;
	}

}
