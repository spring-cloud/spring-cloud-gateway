package org.springframework.cloud.gateway.test;

import com.google.common.collect.Sets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;

public class RouteConstructionIntegrationTests {

	@Rule
	public ExpectedException rule = ExpectedException.none();

	@Test(expected = Throwable.class)
	public void routesWithVerificationShouldFail() {
		final SpringApplication springApplication = new SpringApplication();
		springApplication.setAdditionalProfiles("verification-route");
		springApplication.setSources(Sets.newHashSet(RouteConstructionIntegrationTests.TestConfig.class.getName()));
		springApplication.run();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		@Bean
		public TestFilterGatewayFilterFactory testFilterGatewayFilterFactory(){
			return new TestFilterGatewayFilterFactory();
		}
	}

	public static class TestFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<TestFilterGatewayFilterFactory.Config> {

		public TestFilterGatewayFilterFactory(){
			super(Config.class);
		}

		@Override
		public GatewayFilter apply(Config config) {
			throw new AssertionError("Stop right now!");
		}

		public static class Config {
			private String arg1;

			public String getArg1() {
				return arg1;
			}

			public void setArg1(String arg1) {
				this.arg1 = arg1;
			}
		}
	}

}