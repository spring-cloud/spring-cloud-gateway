/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.log.LogMessage;

class PropertiesMigrationListener implements ApplicationListener<SpringApplicationEvent> {
	private static final Log logger = LogFactory.getLog(PropertiesMigrationListener.class);
	public static final String DEPRECATED_ROOT = "spring.cloud.gateway.mvc";
	public static final String ROUTES_KEY = DEPRECATED_ROOT + ".routes[";
	public static final String ROUTES_MAP_KEY = DEPRECATED_ROOT + ".routes-map.";
	public static final String ROUTES_MAP2_KEY = DEPRECATED_ROOT + ".routesMap.";

	private boolean hasRoutes = false;

	@Override
	public void onApplicationEvent(SpringApplicationEvent event) {
		if (event instanceof ApplicationPreparedEvent preparedEvent) {
			onApplicationPreparedEvent(preparedEvent);
		}
		if (event instanceof ApplicationReadyEvent || event instanceof ApplicationFailedEvent) {
			logLegacyPropertiesReport();
		}
	}

	private void onApplicationPreparedEvent(ApplicationPreparedEvent event) {
		// find deprecated keys
		ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();

		List<ConfigurationPropertyName> deprecatedRoutesKeys = new ArrayList<>();
		List<ConfigurationPropertyName> deprecatedRoutesMapsKeys = new ArrayList<>();
		ConfigurationPropertySources.get(env).forEach(propertySource -> {
			ConfigurationPropertyName routesParentName = ConfigurationPropertyName.of(DEPRECATED_ROOT + ".routes");
			if (propertySource instanceof IterableConfigurationPropertySource iterableSource) {
				List<ConfigurationPropertyName> routesKeys = iterableSource.filter(n -> {
					if (n.getNumberOfElements() < routesParentName.getNumberOfElements()) {
						return false;
					}
					ConfigurationPropertyName parent = n.getParent();
					ConfigurationPropertyName subname = n.subName(routesParentName.getNumberOfElements());
					ConfigurationPropertyName chop = n.chop(routesParentName.getNumberOfElements());
					return routesParentName.equals(chop);
				}).stream().toList();
				if (!routesKeys.isEmpty()) {
					deprecatedRoutesKeys.addAll(routesKeys);
					String name;
					if (propertySource.getUnderlyingSource() instanceof PropertySource<?> underlyingSource) {
						name = underlyingSource.getName();
					} else {
						name = propertySource.getUnderlyingSource().toString();
					}
					String target = "migrate-" + name;
					Map<String, OriginTrackedValue> content = new LinkedHashMap<>();
					for (ConfigurationPropertyName routesKey : routesKeys) {
						ConfigurationPropertyName suffix = routesKey.subName(routesParentName.getNumberOfElements());
						ConfigurationPropertyName newKey = ConfigurationPropertyName.of(GatewayMvcProperties.PREFIX+".routes").append(suffix);
						ConfigurationProperty configurationProperty = propertySource.getConfigurationProperty(routesKey);
						Object value = configurationProperty.getValue();
						OriginTrackedValue originTrackedValue = OriginTrackedValue.of(value, configurationProperty.getOrigin());
						content.put(newKey.toString(), originTrackedValue);
					}
					env.getPropertySources().addBefore(name, new OriginTrackedMapPropertySource(target, content));
				}
			}
			/*List<String> routesKeys = Arrays.stream(source.getPropertyNames()).filter(s -> s.startsWith(ROUTES_KEY)).toList();
			if (!routesKeys.isEmpty()) {
				deprecatedRoutesKeys.addAll(routesKeys);

				LinkedHashMap<String, Object> map = new LinkedHashMap<>();
				MapPropertySource migratedPropertySource = new MapPropertySource("migrate-" + source.getName(), map);
				routesKeys.forEach(key -> {
					String replacement = key.replace(DEPRECATED_ROOT, "spring.cloud.gateway.server.webmvc");
					Object property = source.getProperty(key);
					map.put(replacement, property);
				});
				env.getPropertySources().addBefore(source.getName(), migratedPropertySource);
			}
			List<String> routeMapKeys = Arrays.stream(source.getPropertyNames()).filter(s -> s.startsWith(ROUTES_MAP_KEY) || s.startsWith(ROUTES_MAP2_KEY)).toList();
			if (!routeMapKeys.isEmpty()) {
				deprecatedRoutesMapsKeys.addAll(routeMapKeys);
			}*/
		});
		hasRoutes = !deprecatedRoutesKeys.isEmpty() || !deprecatedRoutesMapsKeys.isEmpty();
		// migrate to new keys
	}

	private void logLegacyPropertiesReport() {
		// log warnings
		if (hasRoutes) {
			logger.warn(LogMessage.format("%s routes were found", ""));
		}
	}
}
