/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.client;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.rsocket.client.ClientProperties.TagKey;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = "spring.cloud.gateway.rsocket.client.auto-connect=false")
public class ClientPropertiesTests {

	@Autowired
	ClientProperties properties;

	@Test
	public void clientProperties() {
		assertThat(properties).isNotNull();
		assertThat(properties.getRouteId()).isEqualTo(11L);
		assertThat(properties.getServiceName()).isEqualTo("test_requester");
		assertThat(properties.getTags()).containsEntry(TagKey.of("INSTANCE_NAME"),
				"test_requester1");
		assertThat(properties.getForwarding()).containsKey("test_responder-rc");
		Map<TagKey, String> map = properties.getForwarding().get("test_responder-rc");
		assertThat(map).contains(entry(TagKey.of("SERVICE_NAME"), "test_responder"),
				entry(TagKey.of("custom-tag"), "custom-value"));
		assertThat(properties.getBroker()).isNotNull().extracting("host", "port")
				.containsExactly("localhost", 7002);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Config {

	}

}
