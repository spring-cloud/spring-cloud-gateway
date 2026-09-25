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

package org.springframework.cloud.gateway.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory.CacheMetricsListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Auto-configuration that exposes cache statistics for {@code LocalResponseCache} as
 * Micrometer meters. Registers a {@link CacheMetricsListener} that binds each Caffeine
 * cache to the {@link MeterRegistry} the first time it is created and rebinds the
 * underlying reference on subsequent route refreshes so the gauges keep tracking the
 * current cache instance.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".enabled", matchIfMissing = true)
@AutoConfigureAfter({ LocalResponseCacheAutoConfiguration.class, MetricsAutoConfiguration.class,
		CompositeMeterRegistryAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, Caffeine.class, CaffeineCacheManager.class, MeterRegistry.class,
		MetricsAutoConfiguration.class })
public class LocalResponseCacheMetricsAutoConfiguration {

	@Bean
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.enabled", matchIfMissing = true)
	public CacheMetricsListener localResponseCacheMetricsListener(MeterRegistry meterRegistry) {
		return new SwappableCacheMetricsListener(meterRegistry);
	}

	/**
	 * Listener that binds each cache name to the registry once and swaps the underlying
	 * cache reference on subsequent invocations.
	 *
	 * <p>
	 * {@link MeterRegistry} silently drops duplicate registrations - calling
	 * {@code CaffeineCacheMetrics.monitor(...)} a second time with a new cache instance
	 * returns the existing meter, leaving the gauges bound to the original (already
	 * replaced) cache. Because {@code LocalResponseCacheGatewayFilterFactory.apply} is
	 * re-invoked on every route refresh and builds a new Caffeine cache each time, naive
	 * registration would leave the metrics permanently tracking a discarded cache and
	 * reporting {@code NaN} once it is garbage-collected.
	 */
	static class SwappableCacheMetricsListener implements CacheMetricsListener {

		private final MeterRegistry registry;

		private final Map<String, AtomicReference<Cache<?, ?>>> refsByCacheName = new ConcurrentHashMap<>();

		SwappableCacheMetricsListener(MeterRegistry registry) {
			this.registry = registry;
		}

		@Override
		public void onCacheCreated(Cache<?, ?> cache, String cacheName) {
			refsByCacheName.computeIfAbsent(cacheName, name -> {
				AtomicReference<Cache<?, ?>> ref = new AtomicReference<>(cache);
				new SwappableCaffeineCacheMetrics(ref, name, Tags.empty()).bindTo(registry);
				return ref;
			}).set(cache);
		}

	}

	/**
	 * Cache meter binder bound to a mutable {@link AtomicReference} so the registered
	 * gauges always read whichever Caffeine cache instance is currently set on the
	 * reference. Exposes the standard {@link CacheMeterBinder} meter set
	 * ({@code cache.size}, {@code cache.gets}, {@code cache.puts},
	 * {@code cache.evictions}); Caffeine-specific meters are intentionally omitted to
	 * keep the refresh-safe path simple.
	 */
	static class SwappableCaffeineCacheMetrics extends CacheMeterBinder<AtomicReference<Cache<?, ?>>> {

		SwappableCaffeineCacheMetrics(AtomicReference<Cache<?, ?>> ref, String cacheName, Iterable<Tag> tags) {
			super(ref, cacheName, tags);
		}

		@Override
		protected @Nullable Long size() {
			Cache<?, ?> c = current();
			return c != null ? c.estimatedSize() : null;
		}

		@Override
		protected long hitCount() {
			Cache<?, ?> c = current();
			return c != null ? c.stats().hitCount() : 0L;
		}

		@Override
		protected @Nullable Long missCount() {
			Cache<?, ?> c = current();
			return c != null ? c.stats().missCount() : null;
		}

		@Override
		protected @Nullable Long evictionCount() {
			Cache<?, ?> c = current();
			return c != null ? c.stats().evictionCount() : null;
		}

		@Override
		protected long putCount() {
			Cache<?, ?> c = current();
			return c != null ? c.stats().loadCount() : 0L;
		}

		@Override
		protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
			// Intentionally empty - see class javadoc.
		}

		private @Nullable Cache<?, ?> current() {
			AtomicReference<Cache<?, ?>> ref = getCache();
			return ref != null ? ref.get() : null;
		}

	}

}
