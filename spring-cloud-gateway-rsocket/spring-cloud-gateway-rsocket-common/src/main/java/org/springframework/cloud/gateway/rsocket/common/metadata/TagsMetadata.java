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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.rsocket.util.NumberUtils;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.util.Assert;

import static org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey.ROUTE_ID;
import static org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey.SERVICE_NAME;

public class TagsMetadata {

	private static final Key ROUTE_ID_KEY = new Key(ROUTE_ID);

	private static final int WELL_KNOWN_TAG = 0x80;

	private static final int HAS_MORE_TAGS = 0x80;

	private static final int MAX_TAG_LENGTH = 0x7F;

	private final Map<Key, String> tags;

	protected TagsMetadata(Map<Key, String> tags) {
		this.tags = tags;
	}

	public static ByteBuf asByteBuf(DataBuffer buffer) {
		return buffer instanceof NettyDataBuffer
				? ((NettyDataBuffer) buffer).getNativeBuffer()
				: Unpooled.wrappedBuffer(buffer.asByteBuffer());
	}

	public Map<Key, String> getTags() {
		return this.tags;
	}

	public String getRouteId() {
		return this.tags.get(ROUTE_ID_KEY);
	}

	public String get(WellKnownKey key) {
		return this.tags.get(new Key(key));
	}

	public String put(Key key, String value) {
		return this.tags.put(key, value);
	}

	/**
	 * Allows subclasses to enrich tags before use.
	 * @return by default, this.
	 */
	public TagsMetadata getEnrichedTagsMetadata() {
		return this;
	}

	@Override
	public String toString() {
		return "TagsMetadata" + tags;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(TagsMetadata existing) {
		Builder builder = new Builder();
		return builder.with(existing);
	}

	protected static ByteBuf encode(TagsMetadata metadata) {
		return encode(ByteBufAllocator.DEFAULT, metadata.tags);
	}

	protected static ByteBuf encode(ByteBufAllocator allocator, Map<Key, String> tags) {
		Assert.notNull(tags, "tags may not be null");
		Assert.notNull(allocator, "allocator may not be null");
		ByteBuf byteBuf = allocator.buffer();
		return encode(byteBuf, tags);
	}

	protected static ByteBuf encode(ByteBuf byteBuf, Map<Key, String> tags) {
		Assert.notNull(byteBuf, "byteBuf may not be null");

		Iterator<Map.Entry<Key, String>> it = tags.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Key, String> entry = it.next();
			Key key = entry.getKey();
			if (key.wellKnownKey != null) {
				byte id = key.wellKnownKey.getIdentifier();
				int keyLength = WELL_KNOWN_TAG | id;
				byteBuf.writeByte(keyLength);
			}
			else {
				String keyString = key.key;
				if (keyString == null) {
					continue;
				}
				int keyLength = ByteBufUtil.utf8Bytes(keyString);
				if (keyLength == 0 || keyLength > MAX_TAG_LENGTH) {
					continue;
				}
				byteBuf.writeByte(keyLength);
				ByteBufUtil.reserveAndWriteUtf8(byteBuf, keyString, keyLength);
			}

			boolean hasMoreTags = it.hasNext();

			String value = entry.getValue();
			int valueLength = ByteBufUtil.utf8Bytes(value);
			if (valueLength == 0 || valueLength > MAX_TAG_LENGTH) {
				continue;
			}
			int valueByte;
			if (hasMoreTags) {
				valueByte = HAS_MORE_TAGS | valueLength;
			}
			else {
				valueByte = valueLength;
			}
			byteBuf.writeByte(valueByte);
			ByteBufUtil.reserveAndWriteUtf8(byteBuf, value, valueLength);
		}

		return byteBuf;
	}

	protected static void encodeBigInteger(ByteBuf byteBuf, BigInteger bigInteger) {
		byte[] idBytes = bigInteger.toByteArray();
		// truncate or pad to 16 bytes or 128 bits
		// byte[] normalizedBytes = Arrays.copyOf(idBytes, 16);
		byte[] normalizedBytes = new byte[16];
		// right shift
		int destPos = normalizedBytes.length - idBytes.length;
		System.arraycopy(idBytes, 0, normalizedBytes, destPos, idBytes.length);

		byteBuf.writeBytes(normalizedBytes);
	}

	protected static void encodeString(ByteBuf byteBuf, String s) {
		int length = NumberUtils.requireUnsignedByte(ByteBufUtil.utf8Bytes(s));
		byteBuf.writeByte(length);
		ByteBufUtil.reserveAndWriteUtf8(byteBuf, s, length);
	}

	protected static TagsMetadata decode(ByteBuf byteBuf) {
		AtomicInteger offset = new AtomicInteger(0);
		return decode(offset, byteBuf);
	}

	protected static TagsMetadata decode(AtomicInteger offset, ByteBuf byteBuf) {

		Builder builder = TagsMetadata.builder();

		// this means we've reached the end of the buffer
		if (offset.get() >= byteBuf.writerIndex()) {
			return builder.build();
		}

		boolean hasMoreTags = true;

		while (hasMoreTags) {
			int keyByte = byteBuf.getByte(offset.get());
			offset.addAndGet(Byte.BYTES);

			boolean isWellKnownTag = (keyByte & WELL_KNOWN_TAG) == WELL_KNOWN_TAG;

			int keyLength = keyByte & MAX_TAG_LENGTH;

			Key key;
			if (isWellKnownTag) {
				WellKnownKey wellKnownKey = WellKnownKey.fromIdentifier(keyLength);
				key = new Key(wellKnownKey, null);
			}
			else {
				String keyString = byteBuf.toString(offset.get(), keyLength,
						StandardCharsets.UTF_8);
				offset.addAndGet(keyLength);
				key = new Key(null, keyString);
			}

			int valueByte = byteBuf.getByte(offset.get());
			offset.addAndGet(Byte.BYTES);

			hasMoreTags = (valueByte & HAS_MORE_TAGS) == HAS_MORE_TAGS;
			int valueLength = valueByte & MAX_TAG_LENGTH;
			String value = byteBuf.toString(offset.get(), valueLength,
					StandardCharsets.UTF_8);
			offset.addAndGet(valueLength);

			builder.with(key, value);
		}

		return builder.build();
	}

	protected static BigInteger decodeBigInteger(ByteBuf byteBuf, AtomicInteger offset) {
		byte[] idBytes = new byte[16];
		byteBuf.getBytes(offset.get(), idBytes, 0, 16);
		offset.getAndAdd(16);
		return new BigInteger(idBytes);
	}

	protected static long decodeLong(ByteBuf byteBuf, AtomicInteger offset) {
		return byteBuf.getLong(offset.getAndAdd(8));
	}

	protected static String decodeString(ByteBuf byteBuf, AtomicInteger offset) {
		int length = byteBuf.getByte(offset.get());
		int index = offset.addAndGet(Byte.BYTES);
		String s = byteBuf.toString(index, length, StandardCharsets.UTF_8);
		offset.addAndGet(length);
		return s;
	}

	public static class Builder {

		private final TagsMetadata metadata;

		public Builder() {
			this.metadata = new TagsMetadata(new LinkedHashMap<>());
		}

		public Builder with(String key, String value) {
			Assert.notNull(key, "key may not be null");
			return with(new Key(key), value);
		}

		public Builder with(WellKnownKey key, String value) {
			Assert.notNull(key, "key may not be null");
			return with(new Key(key), value);
		}

		public Builder with(Key key, String value) {
			Assert.notNull(key, "key may not be null");
			this.metadata.put(key, value);
			return this;
		}

		public Builder routeId(String routeId) {
			Assert.notNull(routeId, "routeId may not be null");
			return with(ROUTE_ID, routeId);
		}

		public Builder serviceName(String serviceName) {
			Assert.notNull(serviceName, "serviceName may not be null");
			return with(SERVICE_NAME, serviceName);
		}

		public Builder with(TagsMetadata tagsMetadata) {
			this.metadata.getTags().putAll(tagsMetadata.getTags());
			return this;
		}

		public TagsMetadata build() {
			return this.metadata;
		}

		public ByteBuf encode() {
			return TagsMetadata.encode(build());
		}

	}

	public static class Key {

		private final WellKnownKey wellKnownKey;

		private final String key;

		public Key(WellKnownKey wellKnownKey) {
			this(wellKnownKey, null);
		}

		public Key(String key) {
			this(null, key);
		}

		public Key(WellKnownKey wellKnownKey, String key) {
			this.wellKnownKey = wellKnownKey;
			this.key = key;
		}

		public WellKnownKey getWellKnownKey() {
			return this.wellKnownKey;
		}

		public String getKey() {
			return this.key;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Key key1 = (Key) o;
			return this.wellKnownKey == key1.wellKnownKey
					&& Objects.equals(this.key, key1.key);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.wellKnownKey, this.key);
		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(", ", "[", "]");
			if (wellKnownKey != null) {
				joiner.add(wellKnownKey.toString());
				joiner.add(String.format("0x%02x", wellKnownKey.getIdentifier()));
			}
			if (key != null) {
				joiner.add("'" + key + "'");
			}
			return joiner.toString();
		}

	}

}
