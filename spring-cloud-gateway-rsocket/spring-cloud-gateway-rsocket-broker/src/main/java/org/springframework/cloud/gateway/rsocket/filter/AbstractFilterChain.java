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

package org.springframework.cloud.gateway.rsocket.filter;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter.Success;
import org.springframework.lang.Nullable;

/**
 * Default implementation of {@link FilterChain}.
 *
 * <p>
 * Each instance of this class represents one link in the chain. The public constructor
 * {@link #AbstractFilterChain(List)} initializes the full chain and represents its first
 * link.
 *
 * <p>
 * This class is immutable and thread-safe. It can be created once and re-used to handle
 * request concurrently.
 *
 * Copied from org.springframework.web.server.handler.AbstractFilterChain
 *
 * @since 5.0
 */
public abstract class AbstractFilterChain<F extends RSocketFilter, E extends RSocketExchange, FC extends AbstractFilterChain>
		implements FilterChain<E> {

	private final Log log = LogFactory.getLog(getClass());

	protected final List<F> allFilters;

	@Nullable
	protected final F currentFilter;

	@Nullable
	protected final FC next;

	/**
	 * Public constructor with the list of filters and the target handler to use.
	 * @param filters the filters ahead of the handler
	 */
	@SuppressWarnings("unchecked")
	protected AbstractFilterChain(List<F> filters) {
		this.allFilters = Collections.unmodifiableList(filters);
		FC chain = initChain(filters);
		this.currentFilter = (F) chain.currentFilter;
		this.next = (FC) chain.next;
	}

	private FC initChain(List<F> filters) {
		FC chain = create(filters, null, null);
		ListIterator<? extends F> iterator = filters.listIterator(filters.size());
		while (iterator.hasPrevious()) {
			chain = create(filters, iterator.previous(), chain);
		}
		return chain;
	}

	/**
	 * Private constructor to represent one link in the chain.
	 */
	protected AbstractFilterChain(List<F> allFilters, @Nullable F currentFilter,
			@Nullable FC next) {

		this.allFilters = allFilters;
		this.currentFilter = currentFilter;
		this.next = next;
	}

	/**
	 * Private constructor to represent one link in the chain.
	 */
	protected abstract FC create(List<F> allFilters, @Nullable F currentFilter,
			@Nullable FC next);

	public List<F> getFilters() {
		return this.allFilters;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<Success> filter(E exchange) {
		return Mono.defer(() -> this.currentFilter != null && this.next != null
				? this.currentFilter.filter(exchange, this.next) : getMonoSuccess());
	}

	private Mono<Success> getMonoSuccess() {
		if (log.isDebugEnabled()) {
			log.debug("filter chain completed with success");
		}
		return MONO_SUCCESS;
	}

	private static final Mono<Success> MONO_SUCCESS = Mono.just(Success.INSTANCE);

}
