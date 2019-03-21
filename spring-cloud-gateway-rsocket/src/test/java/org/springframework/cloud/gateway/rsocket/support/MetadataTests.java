/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.support;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.rsocket.support.Metadata.matches;

public class MetadataTests {

	@Test
	public void encodeAndDecodeJustName() {
		ByteBuf byteBuf = Metadata.from("test").encode();
		assertMetadata(byteBuf, "test");
	}

	@Test
	public void encodeAndDecodeWorks() {
		ByteBuf byteBuf = Metadata.from("test1").with("key1111", "val111111")
				.with("key22", "val222").encode();
		Metadata metadata = assertMetadata(byteBuf, "test1");
		Map<String, String> properties = metadata.getProperties();
		assertThat(properties).hasSize(2).containsOnlyKeys("key1111", "key22")
				.containsValues("val111111", "val222");
	}

	private Metadata assertMetadata(ByteBuf byteBuf, String name) {
		Metadata metadata = Metadata.decodeMetadata(byteBuf);
		assertThat(metadata).isNotNull();
		assertThat(metadata.getName()).isEqualTo(name);
		return metadata;
	}

	@Test
	public void nullMetadataDoesNotMatch() {
		assertThat(matches(null, new HashMap<>())).isFalse();

		assertThat(matches(new HashMap<>(), null)).isFalse();
	}

	@Test
	public void metadataSubsetMatches() {
		assertThat(matches(metadata(2), metadata(3))).isTrue();
	}

	@Test
	public void metadataEqualSetMatches() {
		assertThat(matches(metadata(3), metadata(3))).isTrue();
	}

	@Test
	public void metadataSuperSetDoesNotMatch() {
		assertThat(matches(metadata(3), metadata(2))).isFalse();
	}

	private Map<String, String> metadata(int size) {
		Assert.isTrue(size > 0, "size must be > 0");
		HashMap<String, String> metadata = new HashMap<>();
		IntStream.rangeClosed(1, size).forEach(i -> metadata.put("key" + i, "val" + i));
		return metadata;
	}

}
