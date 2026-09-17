/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.support;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

/**
 * Opinionated caching helper that defines how to store and restore a {@link Flux} in an
 * arbitrary cache abstraction. A generic writer/reader entry point is provided, but cache
 * vendors that have a Map wrapper support can also be directly used:
 * <p>
 * Generic cache entry points: <pre><code>
 *     AtomicReference&lt;Context> storeRef = new AtomicReference<>(Context.empty());
 *
 *     Flux&lt;Integer> cachedFlux = CacheFlux
 *     		.lookup(k -> Mono.justOrEmpty(storeRef.get().getOrEmpty(k))
 *     		                 .cast(Integer.class)
 *     		                 .flatMap(max -> Flux.range(1, max)
 *     		                                     .materialize()
 *     		                                     .collectList()),
 *     				key)
 *     		.onCacheMissResume(Flux.range(1, 10))
 *     		.andWriteWith((k, sigs) -> Flux.fromIterable(sigs)
 *     		                               .dematerialize()
 *     		                               .last()
 *     		                               .doOnNext(max -> storeRef.updateAndGet(ctx -> ctx.put(k, max)))
 *     		                               .then());
 * </code></pre>
 * <p>
 * Map endpoints: <pre><code>
 *    String key = "myCategory";
 *    LoadingCache&lt;String, Object> graphs = Caffeine
 *        .newBuilder()
 *        .maximumSize(10_000)
 *        .expireAfterWrite(5, TimeUnit.MINUTES)
 *        .refreshAfterWrite(1, TimeUnit.MINUTES)
 *        .build(key -> createExpensiveGraph(key));
 *
 *    Flux&lt;Integer> cachedMyCategory = CacheFlux
 *        .lookup(graphs.asMap(), key, Integer.class)
 *        .onCacheMissResume(repository.findAllByCategory(key));
 * </code></pre>
 * </p>
 *
 * @author Oleh Dokuka
 * @author Simon Baslé
 */
public final class CacheFlux {

	private CacheFlux() {
	}

	/**
	 * Restore a {@link Flux Flux&lt;VALUE&gt;} from the cache-map given a provided key.
	 * The cache is expected to store original values as a {@link List} of {@link Signal}
	 * of T. If no value is in the cache, it will be calculated from the original source
	 * which is set up in the next step. Note that if the source completes empty, this
	 * result will be cached and all subsequent requests with the same key will return
	 * {@link Flux#empty()}. The behaviour is similar for erroring sources, except cache
	 * hits would then return {@link Flux#error(Throwable)}.
	 * <p>
	 * Note that the wrapped {@link Flux} is lazy, meaning that subscribing twice in a row
	 * to the returned {@link Flux} on an empty cache will trigger a cache miss then a
	 * cache hit.
	 * @param cacheMap {@link Map} wrapper of a cache
	 * @param key mapped key
	 * @param <KEY> Key Type
	 * @param <VALUE> Value Type
	 * @return The next {@link FluxCacheBuilderMapMiss builder step} to use to set up the
	 * source
	 */
	public static <KEY, VALUE> FluxCacheBuilderMapMiss<VALUE> lookup(Map<KEY, ? super List> cacheMap, KEY key,
			Class<VALUE> valueClass) {
		return otherSupplier -> Flux.defer(() -> {
			Object fromCache = cacheMap.get(key);
			if (fromCache == null) {
				return otherSupplier.get()
					.materialize()
					.collectList()
					.doOnNext(signals -> cacheMap.put(key, signals))
					.flatMapIterable(Function.identity())
					.dematerialize();
			}
			else if (fromCache instanceof List) {
				try {
					@SuppressWarnings("unchecked")
					List<Signal<VALUE>> fromCacheSignals = (List<Signal<VALUE>>) fromCache;
					return Flux.fromIterable(fromCacheSignals).dematerialize();
				}
				catch (Throwable cause) {
					return Flux.error(new IllegalArgumentException(
							"Content of cache for key " + key + " cannot be cast to List<Signal>", cause));
				}
			}
			else {
				return Flux.error(new IllegalArgumentException("Content of cache for key " + key + " is not a List"));
			}
		});
	}

	/**
	 * Restore a {@link Flux Flux&lt;VALUE&gt;} from the {@link Function cache reader
	 * Function} given a provided key. The cache is expected to store original values as a
	 * {@link List} of {@link Signal} of T. If no value is in the cache, it will be
	 * calculated from the original source which is set up in the next step. Note that if
	 * the source completes empty, this result will be cached and all subsequent requests
	 * with the same key will return {@link Flux#empty()}. The behaviour is similar for
	 * erroring sources, except cache hits would then return
	 * {@link Flux#error(Throwable)}.
	 * <p>
	 * Note that the wrapped {@link Flux} is lazy, meaning that subscribing twice in a row
	 * to the returned {@link Flux} on an empty cache will trigger a cache miss then a
	 * cache hit.
	 * @param reader a {@link Function cache reader Function} function that looks up
	 * collection of {@link Signal} from a cache
	 * @param key mapped key
	 * @param <KEY> Key Type
	 * @param <VALUE> Value Type
	 * @return The next {@link FluxCacheBuilderCacheMiss builder step} used to set up the
	 * source
	 */
	public static <KEY, VALUE> FluxCacheBuilderCacheMiss<KEY, VALUE> lookup(
			Function<KEY, Mono<List<Signal<VALUE>>>> reader, KEY key) {
		return otherSupplier -> (BiFunction<KEY, List<Signal<VALUE>>, Mono<Void>> writer) -> Flux
			.defer(() -> reader.apply(key)
				.switchIfEmpty(Flux.defer(otherSupplier)
					.materialize()
					.collectList()
					.flatMap(signals -> writer.apply(key, signals).then(Mono.just(signals))))
				.flatMapIterable(Function.identity())
				.dematerialize());
	}

	// ==== Flux Builders ====

	/**
	 * Setup original source to fallback to in case of cache miss.
	 *
	 * @param <KEY> Key type
	 * @param <VALUE> Value type
	 */
	public interface FluxCacheBuilderCacheMiss<KEY, VALUE> {

		/**
		 * Setup original source to fallback to in case of cache miss.
		 * @param other original source
		 * @return The {@link FluxCacheBuilderCacheWriter next step} of the builder
		 */
		default FluxCacheBuilderCacheWriter<KEY, VALUE> onCacheMissResume(Flux<VALUE> other) {
			return onCacheMissResume(() -> other);
		}

		/**
		 * Setup original source to fallback to in case of cache miss, through a
		 * {@link Supplier} which allows lazy resolution of the source.
		 * @param otherSupplier original source {@link Supplier}
		 * @return The {@link FluxCacheBuilderCacheWriter next step} of the builder
		 */
		FluxCacheBuilderCacheWriter<KEY, VALUE> onCacheMissResume(Supplier<Flux<VALUE>> otherSupplier);

	}

	/**
	 * Set up the {@link BiFunction cache writer BiFunction} to use to store the source
	 * data into the cache in case of cache miss. The source {@link Flux} is materialized
	 * into a {@link List} of {@link Signal} (completion/error signals included) to be
	 * stored.
	 *
	 * @param <KEY> Key type
	 * @param <VALUE> Value type
	 */
	public interface FluxCacheBuilderCacheWriter<KEY, VALUE> {

		/**
		 * Set up the {@link BiFunction cache writer BiFunction} to use to store the
		 * source data into the cache in case of cache miss. The source {@link Flux} is
		 * materialized into a {@link List} of {@link Signal} (completion/error signals
		 * included) to be stored.
		 * @param writer {@link BiFunction} of key-signals to {@link Mono
		 * Mono&lt;Void&gt;} (which represents the completion of the cache write
		 * operation.
		 * @return A wrapped {@link Flux} that transparently looks up data from a cache
		 * and store data into the cache.
		 */
		Flux<VALUE> andWriteWith(BiFunction<KEY, List<Signal<VALUE>>, Mono<Void>> writer);

	}

	/**
	 * Setup original source to fallback to in case of cache miss and return a wrapped
	 * {@link Flux} that transparently looks up data from a {@link Map} representation of
	 * a cache and store data into the cache in case of cache miss.
	 *
	 * @param <VALUE> type
	 */
	public interface FluxCacheBuilderMapMiss<VALUE> {

		/**
		 * Setup original source to fallback to in case of cache miss.
		 * @param other original source
		 * @return A wrapped {@link Flux} that transparently looks up data from a cache
		 * and store data into the cache.
		 */
		default Flux<VALUE> onCacheMissResume(Flux<VALUE> other) {
			return onCacheMissResume(() -> other);
		}

		/**
		 * Setup original source to fallback to in case of cache miss, through a
		 * {@link Supplier} which allows lazy resolution of the source.
		 * @param otherSupplier original source {@link Supplier}
		 * @return A wrapped {@link Flux} that transparently looks up data from a cache
		 * and store data into the cache.
		 */
		Flux<VALUE> onCacheMissResume(Supplier<Flux<VALUE>> otherSupplier);

	}

}
