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
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;

class GatewayServerWebMvcPropertiesMigrationListener implements ApplicationListener<SpringApplicationEvent> {

	private static final Log logger = LogFactory.getLog(GatewayServerWebMvcPropertiesMigrationListener.class);

	private static final String PROPERTIES_MIGRATOR_CLASS = "org.springframework.boot.context.properties.migrator.PropertiesMigrationListener";

	private static final String DEPRECATED_ROOT = "spring.cloud.gateway.mvc";

	private static final String DEPRECATED_ROUTES_LIST_KEY = DEPRECATED_ROOT + ".routes";

	private static final String DEPRECATED_ROUTES_MAP_KEY = DEPRECATED_ROOT + ".routes-map";

	private static final String GATEWAY_PROPERTY_SOURCE_PREFIX = "migrategatewaymvc";

	private static final String NEW_ROUTES_LIST_KEY = GatewayMvcProperties.PREFIX + ".routes";

	private static final String NEW_ROUTES_MAP_KEY = GatewayMvcProperties.PREFIX + ".routes-map";

	private final List<Migration> routesMigrations = new ArrayList<>();

	@Override
	public void onApplicationEvent(SpringApplicationEvent event) {
		// only run if spring-boot-properties-migrator is on the classpath
		if (!ClassUtils.isPresent(PROPERTIES_MIGRATOR_CLASS, null)) {
			return;
		}
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

		ConfigurationPropertySources.get(env).forEach(propertySource -> {
			routesMigrations.addAll(migrate(env, propertySource, GATEWAY_PROPERTY_SOURCE_PREFIX + "routes-",
					DEPRECATED_ROUTES_LIST_KEY, NEW_ROUTES_LIST_KEY));
			routesMigrations.addAll(migrate(env, propertySource, GATEWAY_PROPERTY_SOURCE_PREFIX + "routes-map-",
					DEPRECATED_ROUTES_MAP_KEY, NEW_ROUTES_MAP_KEY));
		});
	}

	private List<Migration> migrate(ConfigurableEnvironment env, ConfigurationPropertySource propertySource,
			String propertySourcePrefix, String deprecatedKey, String newKeyPrefix) {
		List<Migration> migrations = new ArrayList<>();

		if (propertySource instanceof IterableConfigurationPropertySource iterableSource) {
			ConfigurationPropertyName routesParentName = ConfigurationPropertyName.of(deprecatedKey);
			List<ConfigurationPropertyName> matchingConfigProps = iterableSource.filter(n -> {
				if (n.getNumberOfElements() < routesParentName.getNumberOfElements()) {
					return false;
				}
				ConfigurationPropertyName chop = n.chop(routesParentName.getNumberOfElements());
				return routesParentName.equals(chop);
			}).stream().toList();
			if (!matchingConfigProps.isEmpty()) {
				String originalPropertySourceName;
				if (propertySource.getUnderlyingSource() instanceof PropertySource<?> underlyingSource) {
					originalPropertySourceName = underlyingSource.getName();
				}
				else {
					originalPropertySourceName = propertySource.getUnderlyingSource().toString();
				}
				String newPropertySourceName = propertySourcePrefix + originalPropertySourceName;
				Map<String, OriginTrackedValue> content = new LinkedHashMap<>();
				// migrate to new keys
				for (ConfigurationPropertyName originalPropertyName : matchingConfigProps) {
					ConfigurationPropertyName suffix = originalPropertyName
						.subName(routesParentName.getNumberOfElements());
					ConfigurationPropertyName newProperty = ConfigurationPropertyName.of(newKeyPrefix).append(suffix);
					ConfigurationProperty configurationProperty = propertySource
						.getConfigurationProperty(originalPropertyName);
					Object value = configurationProperty.getValue();
					OriginTrackedValue originTrackedValue = OriginTrackedValue.of(value,
							configurationProperty.getOrigin());
					content.put(newProperty.toString(), originTrackedValue);
					migrations.add(new Migration(originalPropertySourceName, originalPropertyName,
							configurationProperty, newProperty));
				}
				env.getPropertySources()
					.addBefore(originalPropertySourceName,
							new OriginTrackedMapPropertySource(newPropertySourceName, content));
			}
		}
		return migrations;
	}

	private void logLegacyPropertiesReport() {
		// log warnings
		if (!routesMigrations.isEmpty()) {
			LinkedMultiValueMap<String, Migration> content = new LinkedMultiValueMap<>();
			routesMigrations.forEach(migration -> content.add(migration.originalPropertySourceName(), migration));

			StringBuilder report = new StringBuilder();
			report.append(String
				.format("%nThe use of configuration keys that have been renamed was found in the environment:%n%n"));

			content.forEach((name, properties) -> {
				report.append(String.format("Property source '%s':%n", name));
				// properties.sort(PropertyMigration.COMPARATOR);
				properties.forEach((property) -> {
					ConfigurationPropertyName originalPropertyName = property.originalPropertyName();
					report.append(String.format("\tKey: %s%n", originalPropertyName));
					Integer lineNumber = property.determineLineNumber();
					if (lineNumber != null) {
						report.append(String.format("\t\tLine: %d%n", lineNumber));
					}
					report.append(String.format("\t\tReplacement: %s%n", property.newProperty().toString()));
				});
				report.append(String.format("%n"));
			});

			report.append(String.format("%n"));
			report.append("Each configuration key has been temporarily mapped to its "
					+ "replacement for your convenience. To silence this warning, please "
					+ "update your configuration to use the new keys.");
			report.append(String.format("%n"));
			logger.warn(report.toString());
		}
	}

	private record Migration(String originalPropertySourceName, ConfigurationPropertyName originalPropertyName,
			ConfigurationProperty originalProperty, ConfigurationPropertyName newProperty) {

		private Integer determineLineNumber() {
			Origin origin = originalProperty.getOrigin();
			if (origin instanceof PropertySourceOrigin propertySourceOrigin) {
				origin = propertySourceOrigin.getOrigin();
			}
			if (origin instanceof TextResourceOrigin textOrigin) {
				if (textOrigin.getLocation() != null) {
					return textOrigin.getLocation().getLine() + 1;
				}
			}
			return null;
		}
	}

}
