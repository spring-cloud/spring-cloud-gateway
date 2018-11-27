package org.springframework.cloud.gateway.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Gateway 目前只和Spring WebFlux兼容，与Spring MVC不兼容。而且Spring MVC不能够在classpath下，因为如果在的话，Spring Boot会优先自动配置Spring MVC。具体的原因是因为大多数使用Spring
 * MVC的程序员同时会使用WebFlux的webClient
 * 该类用于检查项目是否正确导入 spring-boot-starter-webflux 依赖，而不是错误导入 spring-boot-starter-web 依赖
 * Adding both spring-boot-starter-web and spring-boot-starter-webflux modules in your application results in Spring Boot auto-configuring Spring MVC, not WebFlux.
 * This behavior has been chosen because many Spring developers add spring-boot-starter-webflux to their Spring MVC application to use the reactive WebClient.
 * You can still enforce your choice by setting the chosen application type toSpringApplication.setWebApplicationType(WebApplicationType.REACTIVE)
 */
@Configuration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
public class GatewayClassPathWarningAutoConfiguration {

	private static final Log log = LogFactory.getLog(GatewayClassPathWarningAutoConfiguration.class);
	private static final String BORDER = "\n\n**********************************************************\n\n";

	/**
	 * 检查项目是否错误导入 spring-boot-starter-web 依赖。
	 */
	@Configuration
	@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
	protected static class SpringMvcFoundOnClasspathConfiguration {

		public SpringMvcFoundOnClasspathConfiguration() {
			log.warn(BORDER+"Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway at this time. "+
					"Please remove spring-boot-starter-web dependency."+BORDER);
		}

	}
	/**
	 * 检查项目是否正确导入 spring-boot-starter-webflux 依赖
	 */
	@Configuration
	@ConditionalOnMissingClass("org.springframework.web.reactive.DispatcherHandler")
	protected static class WebfluxMissingFromClasspathConfiguration {

		public WebfluxMissingFromClasspathConfiguration() {
			log.warn(BORDER+"Spring Webflux is missing from the classpath, which is required for Spring Cloud Gateway at this time. "+
					"Please add spring-boot-starter-webflux dependency."+BORDER);
		}

	}
}
