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

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.rsocket.client.ClientProperties.TagKey;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata.Key;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.RouteMatcher;
import org.springframework.util.SimpleRouteMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.rsocket.client.ClientRSocketRequester.expand;
import static org.springframework.cloud.gateway.rsocket.client.ClientRSocketRequester.forwarding;
import static org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey.ROUTE_ID;
import static org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey.SERVICE_NAME;

public class ClientRSocketRequesterTests {

	@Test
	public void forwardingWorks() {
		RouteMatcher routeMatcher = new SimpleRouteMatcher(new AntPathMatcher("."));
		RouteMatcher.Route route = routeMatcher.parseRoute("myroute.foo1.bar1");
		LinkedHashMap<TagKey, String> tags = new LinkedHashMap<>();
		tags.put(TagKey.of(SERVICE_NAME), "{foo}");
		tags.put(TagKey.of(ROUTE_ID), "22");
		tags.put(TagKey.of("mycustomkey"), "{foo}-{bar}");
		Forwarding fwd = (Forwarding) forwarding(routeMatcher, route,
				new BigInteger("11"), "myroute.{foo}.{bar}", tags).build();

		assertThat(fwd).isNotNull();
		assertThat(fwd.getEnrichedTagsMetadata().getTags()).isNotEmpty()
				.containsEntry(new Key(SERVICE_NAME), "foo1")
				.containsEntry(new Key(ROUTE_ID), "22")
				.containsEntry(new Key("mycustomkey"), "foo1-bar1");
	}

	@Test
	public void expandArrayVars() {
		String result = expand("myroute.{foo}.{bar}", "foo1", "bar1");
		assertThat(result).isEqualTo("myroute.foo1.bar1");
	}

	@Test
	public void expandMapVars() {
		HashMap<String, Object> map = new HashMap<>();
		map.put("value", "a+b");
		map.put("city", "Z\u00fcrich");
		String result = expand("/hotel list/{city} specials/{value}", map);

		assertThat(result).isEqualTo("/hotel list/Z\u00fcrich specials/a+b");
	}

	@Test
	public void expandPartially() {
		HashMap<String, Object> map = new HashMap<>();
		map.put("city", "Z\u00fcrich");
		String result = expand("/hotel list/{city} specials/{value}", map);

		assertThat(result).isEqualTo("/hotel list/Zürich specials/");
	}

	@Test
	public void expandSimple() {
		HashMap<String, Object> map = new HashMap<>();
		map.put("foo", "1 2");
		map.put("bar", "3 4");
		String result = expand("/{foo} {bar}", map);
		assertThat(result).isEqualTo("/1 2 3 4");
	}

	@Test // SPR-13311
	public void expandWithRegexVar() {
		String template = "/myurl/{name:[a-z]{1,5}}/show";
		Map<String, String> map = Collections.singletonMap("name", "test");
		String result = expand(template, map);
		assertThat(result).isEqualTo("/myurl/test/show");
	}

	@Test // SPR-17630
	public void expandWithMismatchedCurlyBraces() {
		String result = expand("/myurl/{{{{", Collections.emptyMap());
		assertThat(result).isEqualTo("/myurl/{{{{");
	}

}
