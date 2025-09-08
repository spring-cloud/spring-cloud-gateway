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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getUriTemplateVariables;

/**
 * This filter changes the request uri.
 *
 * @author Stepan Mikhailiuk
 */
public class SetRequestUriGatewayFilterFactory
		extends AbstractChangeRequestUriGatewayFilterFactory<SetRequestUriGatewayFilterFactory.Config> {

	private static final Logger log = LoggerFactory.getLogger(SetRequestUriGatewayFilterFactory.class);

	public SetRequestUriGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		// AbstractChangeRequestUriGatewayFilterFactory.apply() returns
		// OrderedGatewayFilter
		OrderedGatewayFilter gatewayFilter = (OrderedGatewayFilter) super.apply(config);
		return new OrderedGatewayFilter(gatewayFilter, gatewayFilter.getOrder()) {
			@Override
			public String toString() {
				return filterToStringCreator(SetRequestUriGatewayFilterFactory.this)
					.append("template", config.getTemplate())
					.toString();
			}
		};
	}

	String getUri(ServerWebExchange exchange, Config config) {
		String template = config.getTemplate();

		if (template.indexOf('{') == -1) {
			return template;
		}

		Map<String, String> variables = getUriTemplateVariables(exchange);
		return UriComponentsBuilder.fromUriString(template).build().expand(variables).toUriString();
	}

	@Override
	protected Optional<URI> determineRequestUri(ServerWebExchange exchange, Config config) {
		try {
			String url = getUri(exchange, config);
			URI uri = URI.create(url);
			if (!uri.isAbsolute()) {
				log.info("Request url is invalid: url={}, error=URI is not absolute", url);
				return Optional.ofNullable(null);
			}
			return Optional.of(uri);
		}
		catch (IllegalArgumentException e) {
			log.info("Request url is invalid : url={}, error={}", config.getTemplate(), e.getMessage());
			return Optional.ofNullable(null);
		}
	}

	public static class Config {

		private String template;

		public String getTemplate() {
			return template;
		}

		public void setTemplate(String template) {
			this.template = template;
		}

	}

}
