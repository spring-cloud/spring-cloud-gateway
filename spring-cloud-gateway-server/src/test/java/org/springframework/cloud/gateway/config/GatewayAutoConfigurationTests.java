/*
 * Copyright 2013-2021 the original author or authors.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.TrustManagerFactory;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.server.WebsocketServerSpec;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.actuate.GatewayLegacyControllerEndpoint;
import org.springframework.cloud.gateway.config.GatewayAutoConfigurationTests.CustomHttpClientFactory.CustomSslConfigurer;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.GRPCRequestHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.GRPCResponseHeadersFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.filter.reactive.HiddenHttpMethodFilter;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GatewayAutoConfigurationTests {

	@Test
	public void noHiddenHttpMethodFilter() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(Config.class, "--spring.jmx.enabled=false",
				"--server.port=0")) {
			assertThat(ctx.getEnvironment().getProperty("spring.webflux.hiddenmethod.filter.enabled"))
					.isEqualTo("false");
			assertThat(ctx.getBeanNamesForType(HiddenHttpMethodFilter.class)).isEmpty();
		}
	}

	@Test
	public void nettyHttpClientDefaults() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						ServerPropertiesConfig.class))
				.withPropertyValues("debug=true").run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					CustomHttpClientFactory factory = context.getBean(CustomHttpClientFactory.class);

					assertThat(factory.connectionProvider).isNotNull();
					assertThat(factory.connectionProvider.maxConnections()).isEqualTo(Integer.MAX_VALUE); // elastic

					assertThat(factory.proxyProvider).isNull();
					assertThat(factory.isSslConfigured()).isFalse();

					assertThat(httpClient.configuration().isAcceptGzip()).isFalse();
					assertThat(httpClient.configuration().loggingHandler()).isNull();
					assertThat(httpClient.configuration().options())
							.doesNotContainKey(ChannelOption.CONNECT_TIMEOUT_MILLIS);
				});
	}

	@Test
	public void nettyHttpClientConfigured() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("spring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager=true",
						"spring.cloud.gateway.httpclient.connect-timeout=10",
						"spring.cloud.gateway.httpclient.response-timeout=10s",
						"spring.cloud.gateway.httpclient.pool.eviction-interval=10s",
						"spring.cloud.gateway.httpclient.pool.type=fixed",
						"spring.cloud.gateway.httpclient.pool.metrics=true",
						"spring.cloud.gateway.httpclient.compression=true",
						"spring.cloud.gateway.httpclient.wiretap=true",
						// greater than integer max value
						"spring.cloud.gateway.httpclient.max-initial-line-length=2147483647",
						"spring.cloud.gateway.httpclient.proxy.host=myhost",
						"spring.cloud.gateway.httpclient.websocket.max-frame-payload-length=1024")
				.run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					CustomHttpClientFactory factory = context.getBean(CustomHttpClientFactory.class);
					HttpClientProperties properties = context.getBean(HttpClientProperties.class);
					assertThat(properties.getMaxInitialLineLength().toBytes()).isLessThanOrEqualTo(Integer.MAX_VALUE);
					assertThat(properties.isCompression()).isEqualTo(true);
					assertThat(properties.getPool().getEvictionInterval()).hasSeconds(10);
					assertThat(properties.getPool().isMetrics()).isEqualTo(true);

					assertThat(httpClient.configuration().isAcceptGzip()).isTrue();
					assertThat(httpClient.configuration().loggingHandler()).isNotNull();
					assertThat(httpClient.configuration().options()).containsKey(ChannelOption.CONNECT_TIMEOUT_MILLIS);
					assertThat(httpClient.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS))
							.isEqualTo(10);

					assertThat(factory.connectionProvider).isNotNull();
					// fixed pool
					assertThat(factory.connectionProvider.maxConnections())
							.isEqualTo(ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS);

					assertThat(factory.proxyProvider).isNotNull();
					assertThat(factory.proxyProvider.build().getAddress().get().getHostName()).isEqualTo("myhost");

					assertThat(factory.isSslConfigured()).isTrue();
					assertThat(factory.isInsecureTrustManagerSet()).isTrue();

					assertThat(context).hasSingleBean(ReactorNettyRequestUpgradeStrategy.class);
					ReactorNettyRequestUpgradeStrategy upgradeStrategy = context
							.getBean(ReactorNettyRequestUpgradeStrategy.class);
					assertThat(upgradeStrategy.getWebsocketServerSpec().maxFramePayloadLength()).isEqualTo(1024);
					assertThat(upgradeStrategy.getWebsocketServerSpec().handlePing()).isTrue();
					assertThat(context).hasSingleBean(ReactorNettyWebSocketClient.class);
					ReactorNettyWebSocketClient webSocketClient = context.getBean(ReactorNettyWebSocketClient.class);
					assertThat(webSocketClient.getWebsocketClientSpec().maxFramePayloadLength()).isEqualTo(1024);
					HttpClientCustomizedConfig config = context.getBean(HttpClientCustomizedConfig.class);
					assertThat(config.called.get()).isTrue();
				});
	}

	@Test
	public void verboseActuatorEnabledByDefault() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(Config.class, "--spring.jmx.enabled=false",
				"--server.port=0", "--management.endpoint.gateway.enabled=true")) {
			assertThat(ctx.getBeanNamesForType(GatewayControllerEndpoint.class)).hasSize(1);
			assertThat(ctx.getBeanNamesForType(GatewayLegacyControllerEndpoint.class)).isEmpty();
		}
	}

	@Test
	public void verboseActuatorDisabled() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(Config.class, "--spring.jmx.enabled=false",
				"--server.port=0", "--spring.cloud.gateway.actuator.verbose.enabled=false",
				"--management.endpoint.gateway.enabled=true")) {
			assertThat(ctx.getBeanNamesForType(GatewayLegacyControllerEndpoint.class)).hasSize(1);
		}
	}

	@Test
	public void tokenRelayBeansAreCreated() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ReactiveSecurityAutoConfiguration.class,
						ReactiveOAuth2ClientAutoConfiguration.class, GatewayReactiveOAuth2AutoConfiguration.class,
						GatewayAutoConfiguration.TokenRelayConfiguration.class))
				.withPropertyValues(
						"spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
						"spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
						"spring.security.oauth2.client.registration[test].provider=testprovider",
						"spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
						"spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
						"spring.security.oauth2.client.registration[test].client-id=login-client")
				.run(context -> {
					assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
					assertThat(context).hasSingleBean(TokenRelayGatewayFilterFactory.class);
				});
	}

	@Test
	public void gatewayReactiveOAuth2AuthorizedClientManagerBacksOffForCustomBean() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ReactiveSecurityAutoConfiguration.class,
						ReactiveOAuth2ClientAutoConfiguration.class, GatewayReactiveOAuth2AutoConfiguration.class))
				.withUserConfiguration(TestReactiveOAuth2AuthorizedClientManagerConfig.class)
				.withPropertyValues(
						"spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
						"spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
						"spring.security.oauth2.client.registration[test].provider=testprovider",
						"spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
						"spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
						"spring.security.oauth2.client.registration[test].client-id=login-client")
				.run(context -> {
					assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
					assertThat(context).hasBean("myReactiveOAuth2AuthorizedClientManager");
				});
	}

	@Test
	public void noTokenRelayFilter() {
		assertThatThrownBy(() -> {
			try (ConfigurableApplicationContext ctx = SpringApplication.run(RouteLocatorBuilderConfig.class,
					"--spring.jmx.enabled=false", "--spring.cloud.gateway.filter.token-relay.enabled=false",
					"--spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
					"--spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
					"--spring.security.oauth2.client.registration[test].provider=testprovider",
					"--spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
					"--spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
					"--spring.security.oauth2.client.registration[test].client-id=login-client", "--server.port=0",
					"--spring.cloud.gateway.actuator.verbose.enabled=false")) {
				assertThat(ctx.getBeanNamesForType(GatewayLegacyControllerEndpoint.class)).hasSize(1);
			}
		}).hasRootCauseInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No TokenRelayGatewayFilterFactory bean was found. Did you include");
	}

	@Test // gh-2159
	public void reactorNettyRequestUpgradeStrategyWebSocketSpecBuilderIsUniquePerRequest()
			throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ReactorNettyRequestUpgradeStrategy strategy = new GatewayAutoConfiguration.NettyConfiguration()
				.reactorNettyRequestUpgradeStrategy(new HttpClientProperties());

		// Method "buildSpec" was introduced for Tests, but has only default visiblity
		Method buildSpec = ReactorNettyRequestUpgradeStrategy.class.getDeclaredMethod("buildSpec", String.class);
		buildSpec.setAccessible(true);
		WebsocketServerSpec spec1 = (WebsocketServerSpec) buildSpec.invoke(strategy, "p1");
		WebsocketServerSpec spec2 = strategy.getWebsocketServerSpec();

		assertThat(spec1.protocols()).isEqualTo("p1");
		assertThat(spec2.protocols()).isNull();
	}

	@Test // gh-2215
	public void webSocketClientSpecBuilderIsUniquePerReactorNettyWebSocketClient()
			throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ReactorNettyWebSocketClient websocketClient = new GatewayAutoConfiguration.NettyConfiguration()
				.reactorNettyWebSocketClient(new HttpClientProperties(), HttpClient.create());

		// Method "buildSpec" has only private visibility
		Method buildSpec = ReactorNettyWebSocketClient.class.getDeclaredMethod("buildSpec", String.class);
		buildSpec.setAccessible(true);
		WebsocketClientSpec spec1 = (WebsocketClientSpec) buildSpec.invoke(websocketClient, "p1");
		WebsocketClientSpec spec2 = websocketClient.getWebsocketClientSpec();

		assertThat(spec1.protocols()).isEqualTo("p1");
		// Protocols should not be cached between requests:
		assertThat(spec2.protocols()).isNull();
	}

	@Test
	public void gRPCFiltersConfiguredWhenHTTP2Enabled() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("server.http2.enabled=true").run(context -> {
					assertThat(context).hasSingleBean(GRPCRequestHeadersFilter.class);
					assertThat(context).hasSingleBean(GRPCResponseHeadersFilter.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					assertThat(httpClient.configuration().protocols()).contains(HttpProtocol.HTTP11, HttpProtocol.H2);
				});
	}

	@Test
	public void gRPCFiltersNotConfiguredWhenHTTP2Disabled() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("server.http2.enabled=false").run(context -> {
					assertThat(context).doesNotHaveBean(GRPCRequestHeadersFilter.class);
					assertThat(context).doesNotHaveBean(GRPCResponseHeadersFilter.class);
				});
	}

	@Test
	public void insecureTrustManagerNotEnabledByDefaultWhenHTTP2Enabled() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("server.http2.enabled=true").run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					CustomHttpClientFactory factory = context.getBean(CustomHttpClientFactory.class);
					assertThat(factory.isInsecureTrustManagerSet()).isFalse();
				});
	}

	@Test
	public void customHttpClientWorks() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, CustomHttpClientConfig.class))
				.run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					assertThat(httpClient).isInstanceOf(CustomHttpClient.class);
				});
	}

	@Configuration
	@EnableConfigurationProperties(ServerProperties.class)
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	protected static class ServerPropertiesConfig {

		@Bean
		@Primary
		CustomHttpClientFactory customHttpClientFactory(HttpClientProperties properties,
				ServerProperties serverProperties, List<HttpClientCustomizer> customizers,
				HttpClientSslConfigurer sslConfigurer) {
			return new CustomHttpClientFactory(properties, serverProperties, sslConfigurer, customizers);
		}

		@Bean
		@Primary
		CustomSslConfigurer customSslContextFactory(ServerProperties serverProperties,
				HttpClientProperties httpClientProperties) {
			return new CustomSslConfigurer(httpClientProperties.getSsl(), serverProperties);
		}

	}

	protected static class CustomHttpClientFactory extends HttpClientFactory {

		private ConnectionProvider connectionProvider;

		private ProxyProvider.Builder proxyProvider;

		private CustomSslConfigurer customSslContextFactory;

		public CustomHttpClientFactory(HttpClientProperties properties, ServerProperties serverProperties,
				HttpClientSslConfigurer sslConfigurer, List<HttpClientCustomizer> customizers) {
			super(properties, serverProperties, sslConfigurer, customizers);
			this.customSslContextFactory = (CustomSslConfigurer) sslConfigurer;
		}

		@Override
		protected ConnectionProvider buildConnectionProvider(HttpClientProperties properties) {
			connectionProvider = super.buildConnectionProvider(properties);
			return connectionProvider;
		}

		@Override
		protected ProxyProvider.Builder configureProxyProvider(HttpClientProperties.Proxy proxy,
				ProxyProvider.TypeSpec proxySpec) {
			proxyProvider = super.configureProxyProvider(proxy, proxySpec);
			return proxyProvider;
		}

		public boolean isSslConfigured() {
			return customSslContextFactory.sslConfigured;
		}

		public boolean isInsecureTrustManagerSet() {
			return customSslContextFactory.insecureTrustManagerSet;
		}

		protected static class CustomSslConfigurer extends HttpClientSslConfigurer {

			boolean sslConfigured;

			boolean insecureTrustManagerSet;

			protected CustomSslConfigurer(HttpClientProperties.Ssl sslProperties, ServerProperties serverProperties) {
				super(sslProperties, serverProperties);
			}

			@Override
			protected void configureSslContext(HttpClientProperties.Ssl ssl,
					SslProvider.SslContextSpec sslContextSpec) {
				sslConfigured = true;
				super.configureSslContext(getSslProperties(), sslContextSpec);
			}

			@Override
			protected void setTrustManager(SslContextBuilder sslContextBuilder, TrustManagerFactory factory) {
				insecureTrustManagerSet = factory == InsecureTrustManagerFactory.INSTANCE;
				super.setTrustManager(sslContextBuilder, factory);
			}

		}

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@EnableConfigurationProperties(ServerProperties.class)
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	protected static class CustomHttpClientConfig {

		@Bean
		public HttpClient customHttpClient() {
			return new CustomHttpClient();
		}

	}

	protected static class CustomHttpClient extends HttpClient {

		@Override
		public HttpClientConfig configuration() {
			return null;
		}

		@Override
		protected HttpClient duplicate() {
			return this;
		}

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class RouteLocatorBuilderConfig {

		@Bean
		public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test", r -> r.alwaysTrue().filters(GatewayFilterSpec::tokenRelay).uri("http://localhost"))
					.build();
		}

	}

	@Configuration
	protected static class HttpClientCustomizedConfig {

		private final AtomicBoolean called = new AtomicBoolean();

		@Bean
		HttpClientCustomizer myCustomCustomizer() {
			return httpClient -> {
				called.compareAndSet(false, true);
				return httpClient;
			};
		}

	}

	@Configuration
	protected static class TestReactiveOAuth2AuthorizedClientManagerConfig {

		@Bean
		ReactiveOAuth2AuthorizedClientManager myReactiveOAuth2AuthorizedClientManager() {
			return authorizeRequest -> null;
		}

	}

}
