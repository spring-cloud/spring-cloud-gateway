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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;

@FunctionalInterface
public interface TrustedProxies {

	/**
	 * Property name.
	 */
	String PROPERTY = GatewayMvcProperties.PREFIX + ".trusted-proxies";

	boolean isTrusted(String host);

	static TrustedProxies from(String trustedProxies) {
		Assert.hasText(trustedProxies, "trustedProxies must not be empty");
		Pattern pattern = Pattern.compile(trustedProxies);
		return value -> pattern.matcher(value).matches();
	}

	/**
	 * Utility method to filter headers based on a predicate.
	 * @param input HttpHeaders to filter.
	 * @param request ServerRequest.
	 * @param inclusionPredicate the predicate to test for header inclusion.
	 * @return filtered HttpHeaders.
	 */
	static HttpHeaders filterHeaders(HttpHeaders input, ServerRequest request, Predicate<String> inclusionPredicate) {
		HttpHeaders updated = new HttpHeaders();

		// copy all headers that match predicate
		for (Map.Entry<String, List<String>> entry : input.headerSet()) {
			if (inclusionPredicate.test(entry.getKey())) {
				updated.addAll(entry.getKey(), entry.getValue());
			}
		}

		return updated;
	}

	class ForwardedTrustedProxiesCondition extends AllNestedConditions {

		public ForwardedTrustedProxiesCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = GatewayMvcProperties.PREFIX + ".forwarded-request-headers-filter.enabled",
				matchIfMissing = true)
		static class OnPropertyEnabled {

		}

		@ConditionalOnPropertyExists
		static class OnTrustedProxiesNotEmpty {

		}

	}

	class NotForwardedTrustedProxiesCondition extends NoneNestedConditions {

		public NotForwardedTrustedProxiesCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@Conditional(ForwardedTrustedProxiesCondition.class)
		static class OnForwardedTrustedProxiesCondition {

		}

	}

	class XForwardedTrustedProxiesCondition extends AllNestedConditions {

		public XForwardedTrustedProxiesCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = XForwardedRequestHeadersFilterProperties.PREFIX + ".enabled",
				matchIfMissing = true)
		static class OnPropertyEnabled {

		}

		@ConditionalOnPropertyExists
		static class OnTrustedProxiesNotEmpty {

		}

	}

	class NotXForwardedTrustedProxiesCondition extends NoneNestedConditions {

		public NotXForwardedTrustedProxiesCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@Conditional(XForwardedTrustedProxiesCondition.class)
		static class OnXForwardedTrustedProxiesCondition {

		}

	}

	class OnPropertyExistsCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			try {
				String property = context.getEnvironment().getProperty(PROPERTY);
				if (!StringUtils.hasText(property)) {
					return ConditionOutcome.noMatch(PROPERTY + " property is not set or is empty.");
				}
				return ConditionOutcome.match(PROPERTY + " property is not empty.");
			}
			catch (NoSuchElementException e) {
				return ConditionOutcome.noMatch("Missing required property " + PROPERTY);
			}
		}

	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Documented
	@Conditional(OnPropertyExistsCondition.class)
	@interface ConditionalOnPropertyExists {

	}

}
