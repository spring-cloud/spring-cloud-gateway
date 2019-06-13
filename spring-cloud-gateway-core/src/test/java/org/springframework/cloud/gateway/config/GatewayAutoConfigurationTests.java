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

package org.springframework.cloud.gateway.config;

import org.junit.Test;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.filter.reactive.HiddenHttpMethodFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayAutoConfigurationTests {

	@Test
	public void noHiddenHttpMethodFilter() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(
				NoHiddenHttpMethodFilterConfig.class, "--spring.jmx.enabled=false",
				"--server.port=0")) {
			assertThat(ctx.getEnvironment()
					.getProperty("spring.webflux.hiddenmethod.filter.enabled"))
							.isEqualTo("false");
			assertThat(ctx.getBeanNamesForType(HiddenHttpMethodFilter.class)).isEmpty();
		}
	}

	@Test
	public void nettyHttpClientDefaults() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class,
						MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class,
						GatewayAutoConfiguration.class))
				.withPropertyValues("debug=true").run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					/*
					 * FIXME: 2.1.0 HttpClientOptions options = httpClient.options();
					 *
					 * PoolResources poolResources = options.getPoolResources();
					 * assertThat(poolResources).isNotNull(); //TODO: howto test
					 * PoolResources
					 *
					 * ClientProxyOptions proxyOptions = options.getProxyOptions();
					 * assertThat(proxyOptions).isNull();
					 *
					 * SslContext sslContext = options.sslContext();
					 * assertThat(sslContext).isNull();
					 */
				});
	}

	@Test
	public void nettyHttpClientConfigured() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class,
						MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class,
						GatewayAutoConfiguration.class))
				.withPropertyValues(
						"spring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager=true",
						"spring.cloud.gateway.httpclient.connect-timeout=10",
						"spring.cloud.gateway.httpclient.response-timeout=10s",
						"spring.cloud.gateway.httpclient.pool.type=fixed",
						"spring.cloud.gateway.httpclient.proxy.host=myhost")
				.run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					/*
					 * FIXME: 2.1.0 HttpClientOptions options = httpClient.options();
					 *
					 * PoolResources poolResources = options.getPoolResources();
					 * assertThat(poolResources).isNotNull(); //TODO: howto test
					 * PoolResources
					 *
					 * ClientProxyOptions proxyOptions = options.getProxyOptions();
					 * assertThat(proxyOptions).isNotNull();
					 * assertThat(proxyOptions.getAddress().get().getHostName()).isEqualTo
					 * ("myhost");
					 *
					 * SslContext sslContext = options.sslContext();
					 * assertThat(sslContext).isNotNull();
					 */
					// TODO: howto test SslContext
				});
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class NoHiddenHttpMethodFilterConfig {

	}

}
