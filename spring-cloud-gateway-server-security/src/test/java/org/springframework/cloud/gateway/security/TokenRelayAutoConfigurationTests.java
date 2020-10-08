/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.cloud.gateway.security;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 *
 */
public class TokenRelayAutoConfigurationTests {

	@Test
	public void beansAreCreated() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ReactiveSecurityAutoConfiguration.class,
						ReactiveOAuth2ClientAutoConfiguration.class, TokenRelayAutoConfiguration.class))
				.withPropertyValues(
						"spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
						"spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
						"spring.security.oauth2.client.registration[test].provider=testprovider",
						"spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
						"spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
						"spring.security.oauth2.client.registration[test].client-id=login-client")
				.withUserConfiguration(TestConfig.class).withPropertyValues("debug=true").run(context -> {
					assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
					assertThat(context).hasSingleBean(TokenRelayGatewayFilterFactory.class);
				});
	}

	@Configuration
	protected static class TestConfig {

	}

}
