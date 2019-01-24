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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.MonoProcessor;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

//TODO: name?
public class Registry {
	private static final Log log = LogFactory.getLog(Registry.class);

	//TODO: List<MonoProcessor<RSocket>>
	private final ConcurrentHashMap<String, MonoProcessor<RSocket>> pendingRequests = new ConcurrentHashMap<>();

	//TODO: List<RSocket>
	private ConcurrentHashMap<String, RSocket> rsockets = new ConcurrentHashMap<>();

	public void register(List<String> tags, RSocket rsocket) {
		Assert.notEmpty(tags, "tags may not be empty");
		log.debug("Registered RSocket: " + tags);
		rsockets.put(tags.get(0), rsocket);
	}

	public RSocket getRegistered(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}
		return rsockets.get(tags.get(0));
	}

	public MonoProcessor<RSocket> getPendingRequests(List<String> tags) {
		if (CollectionUtils.isEmpty(tags)) {
			return null;
		}
		MonoProcessor<RSocket> monoProcessor = this.pendingRequests.get(tags.get(0));
		if (monoProcessor != null) {
			log.debug("Found pending request: " + tags);
			this.pendingRequests.remove(tags.get(0));
		}
		return monoProcessor;
	}

	public void pendingRequest(List<String> tags, MonoProcessor<RSocket> processor) {
		Assert.notEmpty(tags, "tags may not be empty");
		log.debug("Adding pending request: " + tags);
		this.pendingRequests.put(tags.get(0), processor);
	}

}
