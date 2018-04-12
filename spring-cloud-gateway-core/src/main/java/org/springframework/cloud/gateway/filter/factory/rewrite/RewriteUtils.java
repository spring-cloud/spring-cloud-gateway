/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;

public abstract class RewriteUtils {

	public static <T, R> R process(Mono<T> mono, Function<T, R> consumer) {
		MonoProcessor<T> processor = MonoProcessor.create();
		mono.subscribeWith(processor);
		if (processor.isTerminated()) {
			Throwable error = processor.getError();
			if (error != null) {
				throw (RuntimeException) error;
			}
			T peek = processor.peek();

			return consumer.apply(peek);
		}
		else {
			// Should never happen...
			throw new IllegalStateException(
					"SyncInvocableHandlerMethod should have completed synchronously.");
		}
	}

	public static Optional<HttpMessageReader<?>> getHttpMessageReader(CodecConfigurer codecConfigurer, ResolvableType inElementType, MediaType mediaType) {
		List<HttpMessageReader<?>> readers = codecConfigurer.getReaders();
		return readers.stream()
                .filter(r -> r.canRead(inElementType, mediaType))
				.findFirst();
	}

	public static Optional<HttpMessageWriter<?>> getHttpMessageWriter(CodecConfigurer codecConfigurer, ResolvableType outElementType, MediaType mediaType) {
		return codecConfigurer
                                .getWriters().stream()
                                .filter(w -> w.canWrite(outElementType, mediaType))
                                .findFirst();
	}
}
