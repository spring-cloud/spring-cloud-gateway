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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

public final class RouteSetup extends TagsMetadata {

	/**
	 * Forwarding metadata key.
	 */
	public static final String METADATA_KEY = "routesetup";

	/**
	 * Route Setup subtype.
	 */
	public static final String ROUTE_SETUP = "x.rsocket.routesetup.v0";

	/**
	 * Route Setup mime type.
	 */
	public static final MimeType ROUTE_SETUP_MIME_TYPE = new MimeType("message",
			ROUTE_SETUP);

	private final BigInteger id;

	private final String serviceName;

	private RouteSetup(BigInteger id, String serviceName, Map<Key, String> tags) {
		super(tags);
		this.id = id;
		this.serviceName = serviceName;
	}

	public BigInteger getId() {
		return this.id;
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public ByteBuf encode() {
		return encode(this);
	}

	@Override
	public TagsMetadata getEnrichedTagsMetadata() {
		// @formatter:off
		TagsMetadata tagsMetadata = TagsMetadata.builder(this)
				.with(WellKnownKey.SERVICE_NAME, getServiceName())
				.with(WellKnownKey.ROUTE_ID, id == null ? null : getId().toString())
				.build();
		// @formatter:on

		return tagsMetadata;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("id", id)
				.append("serviceName", serviceName)
				.append("tags", getTags())
				.toString();
		// @formatter:on
	}

	public static Builder of(BigInteger id, String serviceName) {
		return new Builder(id, serviceName);
	}

	public static Builder of(Long id, String serviceName) {
		return of(BigInteger.valueOf(id), serviceName);
	}

	static ByteBuf encode(RouteSetup routeSetup) {
		return encode(ByteBufAllocator.DEFAULT, routeSetup);
	}

	static ByteBuf encode(ByteBufAllocator allocator, RouteSetup routeSetup) {
		Assert.notNull(routeSetup, "routeSetup may not be null");
		Assert.notNull(allocator, "allocator may not be null");
		ByteBuf byteBuf = allocator.buffer();

		encodeBigInteger(byteBuf, routeSetup.id);

		encodeString(byteBuf, routeSetup.getServiceName());

		encode(byteBuf, routeSetup.getTags());

		return byteBuf;
	}

	static RouteSetup decodeRouteSetup(ByteBuf byteBuf) {
		AtomicInteger offset = new AtomicInteger(0);

		BigInteger id = decodeBigInteger(byteBuf, offset);

		String serviceName = decodeString(byteBuf, offset);

		TagsMetadata tagsMetadata = decode(offset, byteBuf);

		RouteSetup routeSetup = new RouteSetup(id, serviceName, tagsMetadata.getTags());

		return routeSetup;
	}

	public static class Encoder extends AbstractEncoder<RouteSetup> {

		public Encoder() {
			super(ROUTE_SETUP_MIME_TYPE);
		}

		@Override
		public Flux<DataBuffer> encode(Publisher<? extends RouteSetup> inputStream,
				DataBufferFactory bufferFactory, ResolvableType elementType,
				MimeType mimeType, Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream encoding not supported.");
		}

		@Override
		public DataBuffer encodeValue(RouteSetup value, DataBufferFactory bufferFactory,
				ResolvableType valueType, MimeType mimeType, Map<String, Object> hints) {
			NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
			ByteBuf encoded = RouteSetup.encode(factory.getByteBufAllocator(), value);
			return factory.wrap(encoded);
		}

	}

	public static class Decoder extends AbstractDecoder<RouteSetup> {

		public Decoder() {
			super(ROUTE_SETUP_MIME_TYPE);
		}

		@Override
		public Flux<RouteSetup> decode(Publisher<DataBuffer> inputStream,
				ResolvableType elementType, MimeType mimeType,
				Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream decoding not supported.");
		}

		@Override
		public RouteSetup decode(DataBuffer buffer, ResolvableType targetType,
				MimeType mimeType, Map<String, Object> hints) throws DecodingException {
			ByteBuf byteBuf = TagsMetadata.asByteBuf(buffer);
			return RouteSetup.decodeRouteSetup(byteBuf);
		}

	}

	public static final class Builder {

		private final BigInteger id;

		private final String serviceName;

		private final TagsMetadata.Builder tagsBuilder = TagsMetadata.builder();

		private Builder(BigInteger id, String serviceName) {
			// Assert.notNull(id, "id may not be null");
			// Assert.hasText(serviceName, "serviceName may not be empty");
			this.id = id;
			this.serviceName = serviceName;
		}

		public Builder with(String key, String value) {
			tagsBuilder.with(key, value);
			return this;
		}

		public Builder with(WellKnownKey key, String value) {
			tagsBuilder.with(key, value);
			return this;
		}

		public Builder with(Key key, String value) {
			tagsBuilder.with(key, value);
			return this;
		}

		public Builder with(TagsMetadata tagsMetadata) {
			tagsBuilder.with(tagsMetadata);
			return this;
		}

		public RouteSetup build() {
			return new RouteSetup(id, serviceName, tagsBuilder.build().getTags());
		}

	}

}
