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
import java.util.function.Consumer;

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import org.springframework.cloud.gateway.rsocket.Registry.RegisteredEvent;

public class PendingRequestRSocket extends AbstractRSocket implements Consumer<RegisteredEvent> {

	private final List<String> routingMetadata;
	private final MonoProcessor<RSocket> processor;

	public PendingRequestRSocket(List<String> routingMetadata) {
		this.routingMetadata = routingMetadata;
		this.processor = MonoProcessor.create();
	}

	@Override
	public void accept(RegisteredEvent registeredEvent) {
		if (registeredEvent.matches(this.routingMetadata)) {
			this.processor.onNext(registeredEvent.getRSocket());
			this.processor.onComplete();
		}
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return processor
				.log("pending-request")
				.flatMap(rsocket -> rsocket.fireAndForget(payload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return processor
				.log("pending-request")
				.flatMap(rsocket -> rsocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return processor
				.log("pending-request")
				.flatMapMany(rsocket -> rsocket.requestStream(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return processor
				.log("pending-request")
				.flatMapMany(rsocket -> rsocket.requestChannel(payloads));
	}
}
