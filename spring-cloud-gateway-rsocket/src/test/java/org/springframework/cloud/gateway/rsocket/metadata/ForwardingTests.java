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

package org.springframework.cloud.gateway.rsocket.metadata;

import java.math.BigInteger;
import java.util.LinkedHashMap;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import org.springframework.cloud.gateway.rsocket.metadata.TagsMetadata.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.rsocket.metadata.RouteSetupTests.MAX_BIGINT;
import static org.springframework.cloud.gateway.rsocket.metadata.RouteSetupTests.TWO_BYTE_BIGINT;
import static org.springframework.cloud.gateway.rsocket.metadata.WellKnownKey.REGION;

public class ForwardingTests {

	@Test
	public void encodeAndDecodeWorksMaxBigint() {
		ByteBuf byteBuf = createForwarding(MAX_BIGINT);
		assertForwarding(byteBuf, MAX_BIGINT);
	}

	@Test
	public void encodeAndDecodeWorksMinBigint() {
		ByteBuf byteBuf = createForwarding(BigInteger.ONE);
		assertForwarding(byteBuf, BigInteger.ONE);
	}

	@Test
	public void encodeAndDecodeWorksTwoBytes() {
		ByteBuf byteBuf = createForwarding(TWO_BYTE_BIGINT);
		assertForwarding(byteBuf, TWO_BYTE_BIGINT);
	}

	protected ByteBuf createForwarding(BigInteger originRouteId) {
		LinkedHashMap<Key, String> tags = new LinkedHashMap<>();
		Forwarding forwarding = Forwarding.of(originRouteId).with(REGION, "us-east-1")
				.build();
		return encode(forwarding);
	}

	protected ByteBuf encode(Forwarding forwarding) {
		return forwarding.encode();
	}

	protected void assertForwarding(ByteBuf byteBuf, BigInteger originRouteId) {
		Forwarding forwarding = decode(byteBuf);
		assertThat(forwarding).isNotNull();
		assertThat(forwarding.getOriginRouteId()).isEqualTo(originRouteId);
		assertThat(forwarding.getTags()).hasSize(1).containsOnlyKeys(new Key(REGION))
				.containsValues("us-east-1");
	}

	protected Forwarding decode(ByteBuf byteBuf) {
		return Forwarding.decodeForwarding(byteBuf);
	}

}
