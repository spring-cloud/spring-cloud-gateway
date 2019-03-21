/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * This class is BETA and may be subject to change in a future release.
 * Response who's job it is to gather the Publisher&lt;DataBuffer&gt; from the writeWith message
 * during a call to HttpMessageWriter.write. Also gathers any headers set there.
 */
public class HttpMessageWriterResponse implements ServerHttpResponse {

	private final HttpHeaders headers = new HttpHeaders();
	private final DataBufferFactory dataBufferFactory;

	private Publisher<? extends DataBuffer> body;

    public HttpMessageWriterResponse(DataBufferFactory dataBufferFactory) {
		this.dataBufferFactory = dataBufferFactory;
	}

	@Override
	public HttpHeaders getHeaders() {
		return this.headers;
	}

	@Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        this.body = body;
        return Mono.empty();
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
    	//TODO: is this kosher?
		return writeWith(Flux.from(body)
						.flatMapSequential(p -> p));
    }

    public Publisher<? extends DataBuffer> getBody() {
        return body;
    }

	@Override
	public boolean setStatusCode(HttpStatus status) {
		return false;
	}

	@Override
	public HttpStatus getStatusCode() {
		return null;
	}

	@Override
	public MultiValueMap<String, ResponseCookie> getCookies() {
		return null;
	}

	@Override
	public void addCookie(ResponseCookie cookie) {

	}

	@Override
	public DataBufferFactory bufferFactory() {
		return this.dataBufferFactory;
	}

	@Override
	public void beforeCommit(Supplier<? extends Mono<Void>> action) {

	}

	@Override
	public boolean isCommitted() {
		return false;
	}

	@Override
	public Mono<Void> setComplete() {
		return null;
	}
}
