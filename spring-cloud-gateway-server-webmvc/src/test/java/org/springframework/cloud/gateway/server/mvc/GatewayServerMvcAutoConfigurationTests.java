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

package org.springframework.cloud.gateway.server.mvc;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.SimpleClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.HttpClientsProperties;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientsProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.server.mvc.filter.FilterAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveContentLengthRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHopByHopResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.RemoveHttp2StatusResponseHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.TransferEncodingNormalizationRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctionAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateAutoConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayServerMvcAutoConfigurationTests {

	private static final String cert = """
			-----BEGIN CERTIFICATE-----
			MIIDqzCCApOgAwIBAgIIFMqbpqvipw0wDQYJKoZIhvcNAQELBQAwbDELMAkGA1UE
			BhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExEjAQBgNVBAcTCVBhbG8gQWx0bzEP
			MA0GA1UEChMGVk13YXJlMQ8wDQYDVQQLEwZTcHJpbmcxEjAQBgNVBAMTCWxvY2Fs
			aG9zdDAgFw0yMzA1MDUxMTI2NThaGA8yMTIzMDQxMTExMjY1OFowbDELMAkGA1UE
			BhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExEjAQBgNVBAcTCVBhbG8gQWx0bzEP
			MA0GA1UEChMGVk13YXJlMQ8wDQYDVQQLEwZTcHJpbmcxEjAQBgNVBAMTCWxvY2Fs
			aG9zdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAPwHWxoE3xjRmNdD
			+m+e/aFlr5wEGQUdWSDD613OB1w7kqO/audEp3c6HxDB3GPcEL0amJwXgY6CQMYu
			sythuZX/EZSc2HdilTBu/5T+mbdWe5JkKThpiA0RYeucQfKuB7zv4ypioa4wiR4D
			nPsZXjg95OF8pCzYEssv8wT49v+M3ohWUgfF0FPlMFCSo0YVTuzB1mhDlWKq/jhQ
			11WpTmk/dQX+l6ts6bYIcJt4uItG+a68a4FutuSjZdTAE0f5SOYRBpGH96mjLwEP
			fW8ZjzvKb9g4R2kiuoPxvCDs1Y/8V2yvKqLyn5Tx9x/DjFmOi0DRK/TgELvNceCb
			UDJmhXMCAwEAAaNPME0wHQYDVR0OBBYEFMBIGU1nwix5RS3O5hGLLoMdR1+NMCwG
			A1UdEQQlMCOCCWxvY2FsaG9zdIcQAAAAAAAAAAAAAAAAAAAAAYcEfwAAATANBgkq
			hkiG9w0BAQsFAAOCAQEAhepfJgTFvqSccsT97XdAZfvB0noQx5NSynRV8NWmeOld
			hHP6Fzj6xCxHSYvlUfmX8fVP9EOAuChgcbbuTIVJBu60rnDT21oOOnp8FvNonCV6
			gJ89sCL7wZ77dw2RKIeUFjXXEV3QJhx2wCOVmLxnJspDoKFIEVjfLyiPXKxqe/6b
			dG8zzWDZ6z+M2JNCtVoOGpljpHqMPCmbDktncv6H3dDTZ83bmLj1nbpOU587gAJ8
			fl1PiUDyPRIl2cnOJd+wCHKsyym/FL7yzk0OSEZ81I92LpGd/0b2Ld3m/bpe+C4Z
			ILzLXTnC6AhrLcDc9QN/EO+BiCL52n7EplNLtSn1LQ==
			-----END CERTIFICATE-----
						""";

	private static final String key = """
					-----BEGIN PRIVATE KEY-----
					MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD8B1saBN8Y0ZjX
					Q/pvnv2hZa+cBBkFHVkgw+tdzgdcO5Kjv2rnRKd3Oh8Qwdxj3BC9GpicF4GOgkDG
					LrMrYbmV/xGUnNh3YpUwbv+U/pm3VnuSZCk4aYgNEWHrnEHyrge87+MqYqGuMIke
					A5z7GV44PeThfKQs2BLLL/ME+Pb/jN6IVlIHxdBT5TBQkqNGFU7swdZoQ5Viqv44
					UNdVqU5pP3UF/perbOm2CHCbeLiLRvmuvGuBbrbko2XUwBNH+UjmEQaRh/epoy8B
					D31vGY87ym/YOEdpIrqD8bwg7NWP/Fdsryqi8p+U8fcfw4xZjotA0Sv04BC7zXHg
					m1AyZoVzAgMBAAECggEAfEqiZqANaF+BqXQIb4Dw42ZTJzWsIyYYnPySOGZRoe5t
					QJ03uwtULYv34xtANe1DQgd6SMyc46ugBzzjtprQ3ET5Jhn99U6kdcjf+dpf85dO
					hOEppP0CkDNI39nleinSfh6uIOqYgt/D143/nqQhn8oCdSOzkbwT9KnWh1bC9T7I
					vFjGfElvt1/xl88qYgrWgYLgXaencNGgiv/4/M0FNhiHEGsVC7SCu6kapC/WIQpE
					5IdV+HR+tiLoGZhXlhqorY7QC4xKC4wwafVSiFxqDOQAuK+SMD4TCEv0Aop+c+SE
					YBigVTmgVeJkjK7IkTEhKkAEFmRF5/5w+bZD9FhTNQKBgQD+4fNG1ChSU8RdizZT
					5dPlDyAxpETSCEXFFVGtPPh2j93HDWn7XugNyjn5FylTH507QlabC+5wZqltdIjK
					GRB5MIinQ9/nR2fuwGc9s+0BiSEwNOUB1MWm7wWL/JUIiKq6sTi6sJIfsYg79zco
					qxl5WE94aoINx9Utq1cdWhwJTQKBgQD9IjPksd4Jprz8zMrGLzR8k1gqHyhv24qY
					EJ7jiHKKAP6xllTUYwh1IBSL6w2j5lfZPpIkb4Jlk2KUoX6fN81pWkBC/fTBUSIB
					EHM9bL51+yKEYUbGIy/gANuRbHXsWg3sjUsFTNPN4hGTFk3w2xChCyl/f5us8Lo8
					Z633SNdpvwKBgQCGyDU9XzNzVZihXtx7wS0sE7OSjKtX5cf/UCbA1V0OVUWR3SYO
					J0HPCQFfF0BjFHSwwYPKuaR9C8zMdLNhK5/qdh/NU7czNi9fsZ7moh7SkRFbzJzN
					OxbKD9t/CzJEMQEXeF/nWTfsSpUgILqqZtAxuuFLbAcaAnJYlCKdAumQgQKBgQCK
					mqjJh68pn7gJwGUjoYNe1xtGbSsqHI9F9ovZ0MPO1v6e5M7sQJHH+Fnnxzv/y8e8
					d6tz8e73iX1IHymDKv35uuZHCGF1XOR+qrA/KQUc+vcKf21OXsP/JtkTRs1HLoRD
					S5aRf2DWcfvniyYARSNU2xTM8GWgi2ueWbMDHUp+ZwKBgA/swC+K+Jg5DEWm6Sau
					e6y+eC6S+SoXEKkI3wf7m9aKoZo0y+jh8Gas6gratlc181pSM8O3vZG0n19b493I
					apCFomMLE56zEzvyzfpsNhFhk5MBMCn0LPyzX6MiynRlGyWIj0c99fbHI3pOMufP
					WgmVLTZ8uDcSW1MbdUCwFSk5
					-----END PRIVATE KEY-----
			""";

	@Test
	void filterEnabledPropertiesWork() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(FilterAutoConfiguration.class, PredicateAutoConfiguration.class,
					HandlerFunctionAutoConfiguration.class, GatewayServerMvcAutoConfiguration.class,
					HttpClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
					RestClientAutoConfiguration.class, SslAutoConfiguration.class))
			.withPropertyValues("spring.cloud.gateway.server.webmvc.form-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.forwarded-request-headers-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.remove-content-length-request-headers-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.remove-hop-by-hop-request-headers-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.remove-hop-by-hop-response-headers-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.remove-http2-status-response-headers-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.transfer-encoding-normalization-request-headers-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.weight-calculator-filter.enabled=false",
					"spring.cloud.gateway.server.webmvc.x-forwarded-request-headers-filter.enabled=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean(FormFilter.class);
				assertThat(context).doesNotHaveBean(ForwardedRequestHeadersFilter.class);
				assertThat(context).doesNotHaveBean(RemoveContentLengthRequestHeadersFilter.class);
				assertThat(context).doesNotHaveBean(RemoveHopByHopRequestHeadersFilter.class);
				assertThat(context).doesNotHaveBean(RemoveHopByHopResponseHeadersFilter.class);
				assertThat(context).doesNotHaveBean(RemoveHttp2StatusResponseHeadersFilter.class);
				assertThat(context).doesNotHaveBean(TransferEncodingNormalizationRequestHeadersFilter.class);
				assertThat(context).doesNotHaveBean(WeightCalculatorFilter.class);
				assertThat(context).doesNotHaveBean(XForwardedRequestHeadersFilter.class);
			});
	}

	@Test
	void bootHttpClientPropertiesWork() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(FilterAutoConfiguration.class, PredicateAutoConfiguration.class,
					HandlerFunctionAutoConfiguration.class, GatewayServerMvcAutoConfiguration.class,
					HttpClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
					RestClientAutoConfiguration.class, SslAutoConfiguration.class))
			.withPropertyValues("spring.http.clients.connect-timeout=1s", "spring.http.clients.read-timeout=2s",
					"spring.http.clients.ssl.bundle=mybundle",
					"spring.ssl.bundle.pem.mybundle.keystore.certificate=" + cert,
					"spring.ssl.bundle.pem.mybundle.keystore.key=" + key)
			.run(context -> {
				assertThat(context).hasSingleBean(HttpClientsProperties.class)
					.doesNotHaveBean(ImperativeHttpClientsProperties.class);
				HttpClientsProperties properties = context.getBean(HttpClientsProperties.class);
				assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
				assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
				assertThat(properties.getSsl().getBundle()).isNotNull();
				// cant test redirects because EnvironmentPostProcessor is not run
			});
	}

	@Test
	void settingHttpClientFactoryWorks() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
			.properties("spring.main.web-application-type=none", "spring.http.clients.imperative.factory=simple")
			.run();
		ClientHttpRequestFactoryBuilder<?> builder = context.getBean(ClientHttpRequestFactoryBuilder.class);
		assertThat(builder).isInstanceOf(SimpleClientHttpRequestFactoryBuilder.class);
	}

	@Test
	void loadBalancerFunctionHandlerAdded() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(FilterAutoConfiguration.class, PredicateAutoConfiguration.class,
					HandlerFunctionAutoConfiguration.class, GatewayServerMvcAutoConfiguration.class,
					HttpClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
					RestClientAutoConfiguration.class))
			.run(context -> assertThat(context).hasBean("lbHandlerFunctionDefinition"));
	}

	@Test
	void loadBalancerFunctionHandlerNotAddedWhenNoLoadBalancerClientOnClasspath() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(FilterAutoConfiguration.class, PredicateAutoConfiguration.class,
					HandlerFunctionAutoConfiguration.class, GatewayServerMvcAutoConfiguration.class,
					HttpClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
					RestClientAutoConfiguration.class))
			.withClassLoader(new FilteredClassLoader(LoadBalancerClient.class))
			.run(context -> assertThat(context).doesNotHaveBean("lbHandlerFunctionDefinition"));
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
