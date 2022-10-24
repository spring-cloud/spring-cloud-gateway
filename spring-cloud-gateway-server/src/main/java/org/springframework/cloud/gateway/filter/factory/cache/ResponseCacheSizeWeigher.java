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

import java.nio.Buffer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Weigher;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import org.springframework.http.HttpHeaders;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class ResponseCacheSizeWeigher implements Weigher<String, Object> {

	@Override
	public @NonNegative int weigh(@NonNull String key, @NonNull Object value) {
		if (value instanceof CachedResponse cached) {
			return Optional.of(cached).map(CachedResponse::headers).map(HttpHeaders::getContentLength)
						   .filter(contentLength -> contentLength > -1).map(Long::intValue)
						   .orElseGet(() -> estimateContentLength(cached));
		}
		else {
			return 0;
		}
	}

	private int estimateContentLength(CachedResponse value) {
		return Stream.ofNullable(value.body()).flatMap(List::stream).mapToInt(Buffer::limit).sum();
	}

}
