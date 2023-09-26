/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.cache;

/**
 * When client sends "no-cache" directive in "Cache-Control" header, the response should
 * be re-validated from upstream. There are several strategies that indicates what to do
 * with the new fresh response.
 */
public enum RequestNoCacheDirectiveStrategy {

	/**
	 * Update the cache entry by the fresh response coming from upstream with a new time
	 * to live.
	 */
	UPDATE_CACHE_ENTRY,
	/**
	 * Skip the update. The client will receive the fresh response, other clients will
	 * receive the old entry in cache.
	 */
	SKIP_UPDATE_CACHE_ENTRY

}
