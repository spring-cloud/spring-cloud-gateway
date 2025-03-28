/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.test;

import java.util.HashMap;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

public class HttpbinTestcontainers implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	// https://github.com/mccutchen/go-httpbin
	// https://hub.docker.com/r/mccutchen/go-httpbin
	private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("mccutchen/go-httpbin");

	/**
	 * Default httpbin port.
	 */
	public static final int DEFAULT_PORT = 8080;

	/**
	 * Shared httpbin container.
	 */
	public static GenericContainer<?> container = createContainer();

	public static GenericContainer<?> createContainer() {
		return new GenericContainer<>(DEFAULT_IMAGE_NAME).withExposedPorts(DEFAULT_PORT)
			.waitingFor(new HttpWaitStrategy().forPort(DEFAULT_PORT));
	}

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		start();

		MutablePropertySources sources = context.getEnvironment().getPropertySources();

		if (!sources.contains("httpbinTestcontainer")) {
			Integer mappedPort = container.getMappedPort(DEFAULT_PORT);
			HashMap<String, Object> map = new HashMap<>();
			map.put("httpbin.port", String.valueOf(mappedPort));
			map.put("httpbin.host", container.getHost());

			sources.addFirst(new MapPropertySource("httpbinTestcontainer", map));
		}
	}

	public static void start() {
		if (!container.isRunning()) {
			container.start();
		}
	}

	public static Integer getPort() {
		if (!container.isRunning()) {
			throw new IllegalStateException("httpbin Testcontainer is not running");
		}
		return container.getMappedPort(DEFAULT_PORT);
	}

	public static String getHost() {
		if (!container.isRunning()) {
			throw new IllegalStateException("httpbin Testcontainer is not running");
		}
		return container.getHost();
	}

	public static void initializeSystemProperties() {
		start();

		System.setProperty("httpbin.port", getPort().toString());
		System.setProperty("httpbin.host", getHost());
	}

}
