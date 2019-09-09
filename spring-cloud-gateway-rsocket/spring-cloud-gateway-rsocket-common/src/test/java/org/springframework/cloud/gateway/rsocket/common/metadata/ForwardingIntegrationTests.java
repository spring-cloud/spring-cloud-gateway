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

package org.springframework.cloud.gateway.rsocket.common.metadata;

import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.rsocket.common.test.MetadataEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.rsocket.server.port=0", webEnvironment = RANDOM_PORT)
public class ForwardingIntegrationTests extends ForwardingTests {

	@Autowired
	private RSocketStrategies strategies;

	@Override
	protected ByteBuf encode(Forwarding forwarding) {
		DataBuffer dataBuffer = new MetadataEncoder(Metadata.COMPOSITE_MIME_TYPE,
				strategies).metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE)
						.encode();
		return TagsMetadata.asByteBuf(dataBuffer);
	}

	@Override
	protected Forwarding decode(ByteBuf byteBuf) {
		MetadataExtractor metadataExtractor = strategies.metadataExtractor();
		Payload payload = DefaultPayload.create(Unpooled.EMPTY_BUFFER, byteBuf);
		Map<String, Object> metadata = metadataExtractor.extract(payload,
				Metadata.COMPOSITE_MIME_TYPE);
		assertThat(metadata).containsKey(Forwarding.METADATA_KEY);

		return (Forwarding) metadata.get(Forwarding.METADATA_KEY);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Config {

	}

}
