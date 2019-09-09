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

package org.springframework.cloud.gateway.rsocket.metrics;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.ResponderRSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import org.springframework.util.Assert;

import static reactor.core.publisher.SignalType.CANCEL;
import static reactor.core.publisher.SignalType.ON_COMPLETE;
import static reactor.core.publisher.SignalType.ON_ERROR;

public class MicrometerResponderRSocket implements ResponderRSocket {

	private static final Log log = LogFactory.getLog(MicrometerResponderRSocket.class);

	private final RSocket delegate;

	private final InteractionCounters metadataPush;

	private final InteractionCounters requestChannel;

	private final InteractionCounters requestFireAndForget;

	private final InteractionTimers requestResponse;

	private final InteractionCounters requestStream;

	/**
	 * Creates a new {@link RSocket}.
	 * @param delegate the {@link RSocket} to delegate to
	 * @param meterRegistry the {@link MeterRegistry} to use
	 * @param tags additional tags to attach to {@link Meter}s
	 * @throws IllegalArgumentException if {@code delegate} or {@code meterRegistry} is
	 * {@code null}
	 */
	public MicrometerResponderRSocket(RSocket delegate, MeterRegistry meterRegistry,
			Tag... tags) {
		Assert.notNull(delegate, "delegate must not be null");
		Assert.notNull(meterRegistry, "meterRegistry must not be null");

		this.delegate = delegate;
		this.metadataPush = new InteractionCounters(meterRegistry, "metadata.push", tags);
		this.requestChannel = new InteractionCounters(meterRegistry, "request.channel",
				tags);
		this.requestFireAndForget = new InteractionCounters(meterRegistry, "request.fnf",
				tags);
		this.requestResponse = new InteractionTimers(meterRegistry, "request.response",
				tags);
		this.requestStream = new InteractionCounters(meterRegistry, "request.stream",
				tags);
	}

	@Override
	public void dispose() {
		delegate.dispose();
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return delegate.fireAndForget(payload).doFinally(requestFireAndForget);
	}

	@Override
	public Mono<Void> metadataPush(Payload payload) {
		return delegate.metadataPush(payload).doFinally(metadataPush);
	}

	@Override
	public Mono<Void> onClose() {
		return delegate.onClose();
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return delegate.requestChannel(payloads).doFinally(requestChannel);
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return Mono.defer(() -> {
			Timer.Sample sample = requestResponse.start();

			return delegate.requestResponse(payload)
					.doFinally(signalType -> requestResponse.accept(sample, signalType));
		});
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return delegate.requestStream(payload).doFinally(requestStream);
	}

	@Override
	public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
		if (delegate instanceof ResponderRSocket) {
			ResponderRSocket rSocket = (ResponderRSocket) delegate;
			return rSocket.requestChannel(payload, payloads).doFinally(requestChannel);
		}
		return delegate.requestChannel(payloads).doFinally(requestChannel);
	}

	private static final class InteractionCounters implements Consumer<SignalType> {

		private final Counter cancel;

		private final Counter onComplete;

		private final Counter onError;

		private InteractionCounters(MeterRegistry meterRegistry, String interactionModel,
				Tag... tags) {
			this.cancel = counter(meterRegistry, interactionModel, CANCEL, tags);
			this.onComplete = counter(meterRegistry, interactionModel, ON_COMPLETE, tags);
			this.onError = counter(meterRegistry, interactionModel, ON_ERROR, tags);
		}

		@Override
		public void accept(SignalType signalType) {
			switch (signalType) {
			case CANCEL:
				if (this.cancel != null) {
					this.cancel.increment();
				}
				break;
			case ON_COMPLETE:
				if (this.onComplete != null) {
					this.onComplete.increment();
				}
				break;
			case ON_ERROR:
				if (this.onError != null) {
					this.onError.increment();
				}
				break;
			}
		}

		private Counter counter(MeterRegistry meterRegistry, String interactionModel,
				SignalType signalType, Tag... tags) {

			Tags withType = Tags.of(tags).and("signal.type", signalType.name());
			try {
				return meterRegistry.counter("rsocket." + interactionModel, withType);
			}
			catch (Exception e) {
				if (log.isTraceEnabled()) {
					log.trace("Error creating counter with tags: " + withType, e);
				}
				return null;
			}
		}

	}

	private static final class InteractionTimers
			implements BiConsumer<Timer.Sample, SignalType> {

		private final Timer cancel;

		private final MeterRegistry meterRegistry;

		private final Timer onComplete;

		private final Timer onError;

		private InteractionTimers(MeterRegistry meterRegistry, String interactionModel,
				Tag... tags) {
			this.meterRegistry = meterRegistry;

			this.cancel = timer(meterRegistry, interactionModel, CANCEL, tags);
			this.onComplete = timer(meterRegistry, interactionModel, ON_COMPLETE, tags);
			this.onError = timer(meterRegistry, interactionModel, ON_ERROR, tags);
		}

		@Override
		public void accept(Timer.Sample sample, SignalType signalType) {
			switch (signalType) {
			case CANCEL:
				if (this.cancel != null) {
					sample.stop(this.cancel);
				}
				break;
			case ON_COMPLETE:
				if (this.onComplete != null) {
					sample.stop(this.onComplete);
				}
				break;
			case ON_ERROR:
				if (this.onError != null) {
					sample.stop(this.onError);
				}
				break;
			}
		}

		Timer.Sample start() {
			return Timer.start(meterRegistry);
		}

		private static Timer timer(MeterRegistry meterRegistry, String interactionModel,
				SignalType signalType, Tag... tags) {

			Tags withType = Tags.of(tags).and("signal.type", signalType.name());
			try {
				return meterRegistry.timer("rsocket." + interactionModel, withType);
			}
			catch (Exception e) {
				if (log.isTraceEnabled()) {
					log.trace("Error creating timer with tags: " + withType, e);
				}
				return null;
			}
		}

	}

}
