package org.springframework.cloud.gateway.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class GatewayClassPathWarningAutoConfiguration {

	private static final Log log = LogFactory.getLog(GatewayClassPathWarningAutoConfiguration.class);
	private static final String BORDER = "\n\n**********************************************************\n\n";

	public GatewayClassPathWarningAutoConfiguration() {
		log.warn(BORDER+"Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway at this time. "+
			"Please remove spring-boot-starter-web dependency."+BORDER);
	}
}
