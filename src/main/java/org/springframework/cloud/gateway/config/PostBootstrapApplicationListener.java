package org.springframework.cloud.gateway.config;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.bootstrap.BootstrapApplicationListener.BOOTSTRAP_PROPERTY_SOURCE_NAME;
import static org.springframework.cloud.bootstrap.BootstrapApplicationListener.DEFAULT_ORDER;

/**
 * Reenables reactive http server after bootstrap.
 * TODO: remove when boot 2.0 handles the NONE web case
 * @author Spencer Gibb
 */
public class PostBootstrapApplicationListener
		implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		ConfigurableEnvironment environment = event.getEnvironment();
		// don't listen to events in a bootstrap context
		if (environment.getPropertySources().contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
			return;
		}
		final Map<String, Object> map = Collections.singletonMap("spring.reactive.enabled", "true");
		environment.getPropertySources().addBefore("bootstrap-web-disabled",
				new MapPropertySource("bootstrap-web-reenable", map));
	}

	@Override
	public int getOrder() {
		return DEFAULT_ORDER+1;
	}
}
