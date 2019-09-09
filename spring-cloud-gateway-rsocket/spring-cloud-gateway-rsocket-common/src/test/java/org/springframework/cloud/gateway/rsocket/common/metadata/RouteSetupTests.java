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

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey.REGION;

public class RouteSetupTests {

	static final BigInteger MAX_BIGINT = new BigInteger(
			"170141183460469231731687303715884105727");
	static final BigInteger TWO_BYTE_BIGINT = new BigInteger("128");

	@Test
	public void bigIntegerTest() {
		byte[] bytes = MAX_BIGINT.toByteArray();
		System.out.println("max bytes: " + bytes.length);

		bytes = BigInteger.ONE.toByteArray();
		System.out.println("min bytes: " + bytes.length);

		BigInteger bigInteger = TWO_BYTE_BIGINT;
		bytes = bigInteger.toByteArray();
		System.out.println("16 bytes: " + bytes.length);
	}

	@Test
	public void encodeAndDecodeWorksMaxBigint() {
		ByteBuf byteBuf = createRouteSetup(MAX_BIGINT);
		assertRouteSetup(byteBuf, MAX_BIGINT);
	}

	@Test
	public void encodeAndDecodeWorksMinBigint() {
		ByteBuf byteBuf = createRouteSetup(BigInteger.ONE);
		assertRouteSetup(byteBuf, BigInteger.ONE);
	}

	@Test
	public void encodeAndDecodeWorksTwoBytes() {
		ByteBuf byteBuf = createRouteSetup(TWO_BYTE_BIGINT);
		assertRouteSetup(byteBuf, TWO_BYTE_BIGINT);
	}

	@Test
	public void encodeAndDecodeWorksEmptyTags() {
		ByteBuf byteBuf = createRouteSetup(TWO_BYTE_BIGINT, false);
		assertRouteSetup(byteBuf, TWO_BYTE_BIGINT, false);
	}

	protected ByteBuf createRouteSetup(BigInteger id) {
		return createRouteSetup(id, true);
	}

	protected ByteBuf createRouteSetup(BigInteger id, boolean addTags) {
		RouteSetup.Builder routeSetup = RouteSetup.of(id, "myservice11111111");
		if (addTags) {
			routeSetup.with(REGION, "us-east-1");
		}
		return encode(routeSetup.build());
	}

	protected ByteBuf encode(RouteSetup routeSetup) {
		return routeSetup.encode();
	}

	protected void assertRouteSetup(ByteBuf byteBuf, BigInteger routeId) {
		assertRouteSetup(byteBuf, routeId, true);
	}

	protected void assertRouteSetup(ByteBuf byteBuf, BigInteger routeId,
			boolean addTags) {
		RouteSetup routeSetup = decode(byteBuf);
		assertThat(routeSetup).isNotNull();
		assertThat(routeSetup.getId()).isEqualTo(routeId);
		assertThat(routeSetup.getServiceName()).isEqualTo("myservice11111111");
		if (addTags) {
			assertThat(routeSetup.getTags()).hasSize(1).containsOnlyKeys(new Key(REGION))
					.containsValues("us-east-1");
		}
		else {
			assertThat(routeSetup.getTags()).isEmpty();
		}
	}

	protected RouteSetup decode(ByteBuf byteBuf) {
		return RouteSetup.decodeRouteSetup(byteBuf);
	}

}
