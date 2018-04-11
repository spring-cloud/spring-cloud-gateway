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

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Mono;

public class FakeResponse extends ServerHttpResponseDecorator {

    private Publisher<? extends DataBuffer> body;

    public FakeResponse(ServerHttpResponse delegate) {
        super(delegate);
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        this.body = body;
        return Mono.empty();
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        throw new UnsupportedOperationException("writeAndFlushWith");
    }

    public Publisher<? extends DataBuffer> getBody() {
        return body;
    }
}
