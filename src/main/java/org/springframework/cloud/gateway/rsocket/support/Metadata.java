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
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.util.NumberUtils;

import org.springframework.util.Assert;

public abstract class Metadata {

	public static final String ROUTING_MIME_TYPE = "message/x.rsocket.routing.v0";

	private Metadata() {}

	public static ByteBuf encodeAnnouncement(String... tags) {
		return encodeAnnouncement(ByteBufAllocator.DEFAULT, tags);
	}

	@SuppressWarnings("Duplicates")
	public static ByteBuf encodeAnnouncement(ByteBufAllocator allocator, String... tags) {
		Assert.notEmpty(tags, "tags may not be null or empty"); //TODO: is this true?
		ByteBuf byteBuf = allocator.buffer();

		for (String tag : tags) {
			int tagLength = NumberUtils.requireUnsignedByte(ByteBufUtil.utf8Bytes(tag));
			byteBuf.writeByte(tagLength);
			ByteBufUtil.reserveAndWriteUtf8(byteBuf, tag, tagLength);
		}
		return byteBuf;
	}

	//TODO: either rsocket spec or custom spec
	@SuppressWarnings("Duplicates")
	public static List<String> decodeAnnouncement(ByteBuf byteBuf) {
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

	public static ByteBuf encodeRouting(String... tags) {
		return encodeRouting(ByteBufAllocator.DEFAULT, tags);
	}

	@SuppressWarnings("Duplicates")
	public static ByteBuf encodeRouting(ByteBufAllocator allocator, String... tags) {
		Assert.notEmpty(tags, "tags may not be null or empty"); //TODO: is this true?
		ByteBuf byteBuf = allocator.buffer();

		for (String tag : tags) {
			int tagLength = NumberUtils.requireUnsignedByte(ByteBufUtil.utf8Bytes(tag));
			byteBuf.writeByte(tagLength);
			ByteBufUtil.reserveAndWriteUtf8(byteBuf, tag, tagLength);
		}
		return byteBuf;
	}

	@SuppressWarnings("Duplicates")
	public static List<String> decodeRouting(ByteBuf byteBuf) {
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
}
