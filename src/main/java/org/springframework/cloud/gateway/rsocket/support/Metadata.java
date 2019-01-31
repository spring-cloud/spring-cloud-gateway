/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.rsocket.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.util.NumberUtils;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class Metadata {

	public static final String ROUTING_MIME_TYPE = "message/x.rsocket.routing.v0";

	private Metadata() {}

	public static ByteBuf encodeProperties(Map<String, String> properties) {
		return encodeProperties(ByteBufAllocator.DEFAULT, properties);
	}

	public static ByteBuf encodeProperties(ByteBufAllocator allocator, Map<String, String> properties) {
		Assert.notEmpty(properties, "tags may not be null or empty"); //TODO: is this true?
		List<String> pairs = properties.entrySet().stream()
				.map(entry -> entry.getValue() + ":" + entry.getValue())
				.collect(Collectors.toList());
		return encodeTags(allocator, pairs.toArray(new String[0]));
	}

	public static ByteBuf encodeTags(String... tags) {
		return encodeTags(ByteBufAllocator.DEFAULT, tags);
	}

	public static ByteBuf encodeTags(ByteBufAllocator allocator, String... tags) {
		Assert.notEmpty(tags, "tags may not be null or empty"); //TODO: is this true?
		ByteBuf byteBuf = allocator.buffer();

		for (String tag : tags) {
			int tagLength = NumberUtils.requireUnsignedByte(ByteBufUtil.utf8Bytes(tag));
			byteBuf.writeByte(tagLength);
			ByteBufUtil.reserveAndWriteUtf8(byteBuf, tag, tagLength);
		}
		return byteBuf;
	}

	public static Map<String, String> decodeProperties(ByteBuf byteBuf) {
		return decodeTags(byteBuf).stream()
				.map(Pair::parse)
				.filter(Objects::nonNull)
				.collect(LinkedHashMap::new,
						(map, pair) -> map.put(pair.name, pair.value),
						HashMap::putAll);
	}

	public static List<String> decodeTags(ByteBuf byteBuf) {
		ArrayList<String> tags = new ArrayList<>();

		int offset = 0;
		while (offset < byteBuf.readableBytes()) { //TODO: What is the best conditional here?
			int tagLength = byteBuf.getByte(offset);
			offset += Byte.BYTES;
			String tag = byteBuf.toString(offset, tagLength, StandardCharsets.UTF_8);
			tags.add(tag);
			offset += tagLength;
		}

		return tags;
	}

	/**
	 * A single name value pair.
	 */
	public static class Pair {

		private String name;

		private String value;

		public Pair(String name, String value) {
			Assert.hasLength(name, "Name must not be empty");
			this.name = name;
			this.value = value;
		}

		public static Pair parse(String pair) {
			int index = getSeparatorIndex(pair);
			String name = (index > 0) ? pair.substring(0, index) : pair;
			String value = (index > 0) ? pair.substring(index + 1) : "";
			return of(name.trim(), value.trim());
		}

		private static int getSeparatorIndex(String pair) {
			int colonIndex = pair.indexOf(':');
			int equalIndex = pair.indexOf('=');
			if (colonIndex == -1) {
				return equalIndex;
			}
			if (equalIndex == -1) {
				return colonIndex;
			}
			return Math.min(colonIndex, equalIndex);
		}

		private static Pair of(String name, String value) {
			if (StringUtils.isEmpty(name) && StringUtils.isEmpty(value)) {
				return null;
			}
			return new Pair(name, value);
		}

	}

}
