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

package org.springframework.cloud.gateway.rsocket;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import org.springframework.util.CollectionUtils;

public class Registry {

	//TODO: List<Mono<RSocket>>
	private ConcurrentHashMap<String, Mono<RSocket>> rsockets = new ConcurrentHashMap<>();

	public void put(String key, Mono<RSocket> rsocket) {
		rsockets.put(key, rsocket);
	}

	public Mono<RSocket> find(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return Mono.empty();
		}
		return rsockets.get(tags.get(0));
	}
}
