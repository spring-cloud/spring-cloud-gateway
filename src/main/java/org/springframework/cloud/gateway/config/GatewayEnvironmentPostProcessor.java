package org.springframework.cloud.gateway.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Spencer Gibb
 */
public class GatewayEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final Map<String, Object> PROPERTIES;

	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	static {
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.resources.add-mappings", "false");
		PROPERTIES = Collections.unmodifiableMap(properties);
	}

	@Override
	public int getOrder() {
		return DEFAULT_ORDER;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment configurableEnvironment, SpringApplication springApplication) {
		//TODO: make this configurable?
		//TODO: should I have to do this?
		configurableEnvironment.getPropertySources().addLast(new MapPropertySource("gateway", PROPERTIES));
	}
}

