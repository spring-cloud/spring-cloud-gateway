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

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.MonoProcessor;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

//TODO: name?
public class Registry {
	private static final Log log = LogFactory.getLog(Registry.class);

	private final MultiValueMap<String, MonoProcessor<RSocket>> pendingRequests = new ConcurrentMultiValueMap<>();

	private final MultiValueMap<String, RSocket> rsockets = new ConcurrentMultiValueMap<>();

	public void register(List<String> tags, RSocket rsocket) {
		Assert.notEmpty(tags, "tags may not be empty");
		log.debug("Registered RSocket: " + tags);
		rsockets.add(tags.get(0), rsocket);
	}

	public List<RSocket> getRegistered(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}
		return rsockets.get(tags.get(0));
	}

	public List<MonoProcessor<RSocket>> getPendingRequests(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}
		List<MonoProcessor<RSocket>> monoProcessor = this.pendingRequests.get(tags.get(0));
		if (!CollectionUtils.isEmpty(monoProcessor)) {
			log.debug("Found pending request: " + tags);
			this.pendingRequests.remove(tags.get(0));
		}
		return monoProcessor;
	}

	public void pendingRequest(List<String> tags, MonoProcessor<RSocket> processor) {
		Assert.notEmpty(tags, "tags may not be empty");
		log.debug("Adding pending request: " + tags);
		this.pendingRequests.add(tags.get(0), processor);
	}

}
