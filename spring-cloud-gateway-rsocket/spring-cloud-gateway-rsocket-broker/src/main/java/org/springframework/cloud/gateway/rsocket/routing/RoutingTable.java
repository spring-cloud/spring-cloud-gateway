/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.roaringbitmap.IntConsumer;
import org.roaringbitmap.RoaringBitmap;
import reactor.core.Disposable;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The RoutingTable handles all RSocket connections that have been made that have
 * associated RouteSetup metadata. RSocket connections can then be found based on
 * Forwarding metadata. When a new RSocket is registered, a RegisteredEvent is pushed onto
 * a DirectProcessor that is acting as an event bus for registered Consumers.
 */
public class RoutingTable {

	private static final Log log = LogFactory.getLog(RoutingTable.class);

	AtomicInteger internalRouteId = new AtomicInteger();

	final Map<Integer, String> internalRouteIdToRouteId = new ConcurrentHashMap<>();

	final Map<TagKey, RoaringBitmap> tagsToBitmaps = new ConcurrentHashMap<>();

	final Map<String, RouteEntry> routeEntries = new ConcurrentHashMap<>();

	private final DirectProcessor<RegisteredEvent> registeredEvents = DirectProcessor
			.create();

	private final FluxSink<RegisteredEvent> registeredEventsSink = registeredEvents
			.sink(FluxSink.OverflowStrategy.DROP);

	public RoutingTable() {
	}

	// TODO: Mono<Void>?
	public void register(TagsMetadata tagsMetadata, RSocket rsocket) {
		register(new RouteEntry(rsocket, tagsMetadata));
	}

	private void register(RouteEntry routeEntry) {
		if (log.isDebugEnabled()) {
			log.debug("Registering RSocket: " + routeEntry.tagsMetadata);
		}

		// TODO: only register new route if timestamp is newer
		String routeId = routeEntry.getRouteId();

		if (routeEntries.containsKey(routeId)) {
			throw new IllegalStateException("Route Id already registered: " + routeId);
		}

		int internalId = internalRouteId.incrementAndGet();
		internalRouteIdToRouteId.put(internalId, routeId);
		routeEntries.put(routeId, routeEntry);

		routeEntry.getTags().forEach((key, value) -> {
			// TODO: deal with string keys?
			RoaringBitmap bitmap = tagsToBitmaps.computeIfAbsent(new TagKey(key, value),
					k -> new RoaringBitmap());
			bitmap.add(internalId);
		});

		registeredEventsSink.next(new RegisteredEvent(routeEntry));
	}

	public boolean deregister(TagsMetadata metadata) {
		Assert.notNull(metadata, "metadata may not be null");
		String routeId = metadata.getRouteId();
		if (!StringUtils.hasText(routeId)) {
			if (log.isDebugEnabled()) {
				log.debug("Unable to deregister, no RouteId: " + metadata);
			}
			return false;
		}
		if (log.isDebugEnabled()) {
			log.debug("Deregistering RSocket: " + metadata);
		}

		TagsMetadata findByRouteId = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, routeId).build();
		RoaringBitmap found = find(findByRouteId);

		if (found.isEmpty() || found.getLongCardinality() > 1) {
			if (log.isWarnEnabled()) {
				log.warn("Unable to deregister " + metadata + ", found: "
						+ found.getLongCardinality());
			}
			return false;
		}

		int internalId = found.first();
		internalRouteIdToRouteId.remove(internalId);
		routeEntries.remove(routeId);

		metadata.getTags().forEach((key, value) -> {
			// TODO: deal with string keys?
			TagKey tagKey = new TagKey(key, value);
			if (tagsToBitmaps.containsKey(tagKey)) {
				RoaringBitmap bitmap = tagsToBitmaps.get(tagKey);
				bitmap.remove(internalId);
			}
		});

		// TODO: deregistered event
		return true;
	}

	/**
	 * Finds routeIds of matching routes.
	 * @param tagsMetadata tags to match.
	 * @return all matching routeIds or empty list.
	 */
	public Set<String> findRouteIds(TagsMetadata tagsMetadata) {
		RoaringBitmap found = find(tagsMetadata);
		if (found.isEmpty()) {
			return Collections.emptySet();
		}
		HashSet<String> routeIds = new HashSet<>();
		found.forEach((IntConsumer) internalId -> {
			String routeId = internalRouteIdToRouteId.get(internalId);
			routeIds.add(routeId);
		});
		return routeIds;
	}

	/**
	 * Finds tuples of routeIds and RSockets of matching routes.
	 * @param tagsMetadata tags to match.
	 * @return all matching routeId and RSocket tuples or empty list.
	 */
	public List<Tuple2<String, RSocket>> findRSockets(TagsMetadata tagsMetadata) {
		RoaringBitmap found = find(tagsMetadata);
		if (found.isEmpty()) {
			return Collections.emptyList();
		}
		ArrayList<Tuple2<String, RSocket>> rSockets = new ArrayList<>();
		found.forEach((IntConsumer) internalId -> {
			String routeId = internalRouteIdToRouteId.get(internalId);
			RouteEntry routeEntry = routeEntries.get(routeId);
			RSocket rSocket = routeEntry.getRSocket();
			rSockets.add(Tuples.of(routeId, rSocket));
		});
		return rSockets;
	}

	/**
	 * Finds internal ids of routes.
	 * @param tagsMetadata tags to match
	 * @return bitmap of all internal ids of routes.
	 */
	RoaringBitmap find(TagsMetadata tagsMetadata) {
		RoaringBitmap found = new RoaringBitmap();
		AtomicBoolean first = new AtomicBoolean(true);
		tagsMetadata.getTags().forEach((key, value) -> {
			TagKey tagKey = new TagKey(key, value);
			if (tagsToBitmaps.containsKey(tagKey)) {
				RoaringBitmap search = tagsToBitmaps.get(tagKey);
				if (first.get()) {
					// initiliaze found bitmap with current search
					found.or(search);
					first.compareAndSet(true, false);
				}
				else {
					found.and(search);
				}
			}
		});
		return found;
	}

	public Disposable addListener(Consumer<RegisteredEvent> consumer) {
		return this.registeredEvents.subscribe(consumer);
	}

	public static class RegisteredEvent {

		private final RouteEntry routeEntry;

		public RegisteredEvent(RouteEntry routeEntry) {
			Assert.notNull(routeEntry, "routeEntry may not be null");
			this.routeEntry = routeEntry;
		}

		public TagsMetadata getRoutingMetadata() {
			return this.routeEntry.getTagsMetadata();
		}

		public RSocket getRSocket() {
			return this.routeEntry.getRSocket();
		}

	}

	static class RouteEntry {

		private final RSocket rSocket;

		private final TagsMetadata tagsMetadata;

		private final Long timestamp;

		RouteEntry(RSocket rSocket, TagsMetadata tagsMetadata) {
			this(rSocket, tagsMetadata, System.currentTimeMillis());
		}

		RouteEntry(RSocket rSocket, TagsMetadata tagsMetadata, Long timestamp) {
			Assert.notNull(tagsMetadata, "tagsMetadata may not be null");
			Assert.notNull(rSocket, "RSocket may not be null");
			this.rSocket = rSocket;
			this.tagsMetadata = tagsMetadata;
			this.timestamp = timestamp;
		}

		public RSocket getRSocket() {
			return this.rSocket;
		}

		public TagsMetadata getTagsMetadata() {
			return this.tagsMetadata;
		}

		public Long getTimestamp() {
			return this.timestamp;
		}

		public String getRouteId() {
			return this.tagsMetadata.getRouteId();
		}

		public Map<TagsMetadata.Key, String> getTags() {
			return this.getTagsMetadata().getTags();
		}

		@Override
		public String toString() {
			// @formatter:off
			return new ToStringCreator(this)
					.append("rSocket", rSocket)
					.append("tagsMetadata", tagsMetadata)
					.toString();
			// @formatter:on
		}

	}

	static class TagKey {

		final TagsMetadata.Key key;

		final String value;

		TagKey(TagsMetadata.Key key, String value) {
			// TODO: Assert non null
			this.key = key;
			this.value = value.toLowerCase();
		}

		public TagsMetadata.Key getKey() {
			return this.key;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			TagKey that = (TagKey) o;
			return Objects.equals(this.key, that.key)
					&& Objects.equals(this.value, that.value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.key, this.value);
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("key", key).append("value", value)
					.toString();

		}

	}

}
