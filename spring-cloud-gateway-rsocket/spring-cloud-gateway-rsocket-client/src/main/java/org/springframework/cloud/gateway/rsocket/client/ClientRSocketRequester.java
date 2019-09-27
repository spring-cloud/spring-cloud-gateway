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

package org.springframework.cloud.gateway.rsocket.client;

import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.rsocket.RSocket;

import org.springframework.cloud.gateway.rsocket.client.ClientProperties.TagKey;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.lang.Nullable;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.ObjectUtils;
import org.springframework.util.RouteMatcher;

final class ClientRSocketRequester implements RSocketRequester {

	/** For route variable replacement. */
	private static final Pattern NAMES_PATTERN = Pattern.compile("\\{([^/]+?)\\}");

	private final RSocketRequester delegate;

	private final ClientProperties properties;

	private final RouteMatcher routeMatcher;

	ClientRSocketRequester(RSocketRequester delegate, ClientProperties properties,
			RouteMatcher routeMatcher) {
		this.delegate = delegate;
		this.properties = properties;
		this.routeMatcher = routeMatcher;
	}

	@Override
	public RSocket rsocket() {
		return delegate.rsocket();
	}

	@Override
	public MimeType dataMimeType() {
		return delegate.dataMimeType();
	}

	@Override
	public MimeType metadataMimeType() {
		return delegate.metadataMimeType();
	}

	@Override
	public RequestSpec route(String route, Object... routeVars) {
		RequestSpec requestSpec = delegate.route(route, routeVars);
		// needs to be expanded with routeVars
		RouteMatcher.Route parsed = routeMatcher.parseRoute(expand(route, routeVars));

		properties.getForwarding().entrySet().stream()
				.filter(entry -> routeMatcher.match(entry.getKey(), parsed)).findFirst()
				.ifPresent(entry -> {
					Forwarding.Builder forwarding = forwarding(routeMatcher, parsed,
							properties.getRouteId(), entry.getKey(), entry.getValue());

					requestSpec.metadata(forwarding.build(),
							Forwarding.FORWARDING_MIME_TYPE);
				});

		return requestSpec;
	}

	/* for testing */ static Forwarding.Builder forwarding(RouteMatcher routeMatcher,
			RouteMatcher.Route route, BigInteger originRouteId, String routeKey,
			Map<TagKey, String> tags) {
		Map<String, String> extracted = routeMatcher.matchAndExtract(routeKey, route);
		Forwarding.Builder forwarding = Forwarding.of(originRouteId);

		tags.forEach((tagKey, value) -> {
			if (tagKey.getWellKnownKey() != null) {
				forwarding.with(tagKey.getWellKnownKey(), expand(value, extracted));
			}
			else if (tagKey.getCustomKey() != null) {
				forwarding.with(tagKey.getCustomKey(), expand(value, extracted));
			}
		});
		return forwarding;
	}

	@Override
	public RequestSpec metadata(Object metadata, MimeType mimeType) {
		return delegate.metadata(metadata, mimeType);
	}

	/* for testing */ static String expand(String route, Object... routeVars) {
		if (ObjectUtils.isEmpty(routeVars)) {
			return route;
		}
		StringBuffer sb = new StringBuffer();
		int index = 0;
		Matcher matcher = NAMES_PATTERN.matcher(route);
		while (matcher.find()) {
			Assert.isTrue(index < routeVars.length,
					() -> "No value for variable '" + matcher.group(1) + "'");
			String value = routeVars[index].toString();
			value = value.contains(".") ? value.replaceAll("\\.", "%2E") : value;
			matcher.appendReplacement(sb, value);
			index++;
		}
		return sb.toString();
	}

	/* for testing */ static String expand(String template, Map<String, ?> vars) {
		if (template == null) {
			return null;
		}
		if (template.indexOf('{') == -1) {
			return template;
		}
		if (template.indexOf(':') != -1) {
			template = sanitizeSource(template);
		}

		if (ObjectUtils.isEmpty(vars)) {
			return template;
		}

		StringBuffer sb = new StringBuffer();
		Matcher matcher = NAMES_PATTERN.matcher(template);
		while (matcher.find()) {
			String match = matcher.group(1);
			String varName = getVariableName(match);
			Object varValue = vars.get(varName);
			String formatted = getVariableValueAsString(varValue);
			matcher.appendReplacement(sb, formatted);
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Remove nested "{}" such as in URI vars with regular expressions.
	 */
	private static String sanitizeSource(String source) {
		int level = 0;
		StringBuilder sb = new StringBuilder();
		for (char c : source.toCharArray()) {
			if (c == '{') {
				level++;
			}
			if (c == '}') {
				level--;
			}
			if (level > 1 || (level == 1 && c == '}')) {
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static String getVariableName(String match) {
		int colonIdx = match.indexOf(':');
		return (colonIdx != -1 ? match.substring(0, colonIdx) : match);
	}

	private static String getVariableValueAsString(@Nullable Object variableValue) {
		return (variableValue != null ? variableValue.toString() : "");
	}

}
