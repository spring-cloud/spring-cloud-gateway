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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory.Config;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author hyungzin0309
 */
public class ModifyRequestBodyGatewayFilterFactoryUnitTests {

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setInClass(String.class);
		config.setOutClass(Integer.class);
		config.setContentType("mycontenttype");
		GatewayFilter filter = new ModifyRequestBodyGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("String").contains("Integer").contains("mycontenttype");
	}

	@Test
	public void toStringFormatWithParameterizedTypeReferences() {
		Config config = new Config();
		config.setInClass(new ParameterizedTypeReference<String>() {
		});
		config.setOutClass(new ParameterizedTypeReference<Integer>() {
		});
		config.setContentType("mycontenttype");
		GatewayFilter filter = new ModifyRequestBodyGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("String").contains("Integer").contains("mycontenttype");
	}

	@Test
	public void headersAddedByMutatingTheRequestAreVisibleDownstream() {
		AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();
		AtomicReference<String> downstreamBody = new AtomicReference<>();
		GatewayFilterChain chain = exchange -> {
			ServerHttpRequest request = exchange.getRequest().mutate().header("X-Added", "added").build();
			ServerWebExchange mutated = exchange.mutate().request(request).build();
			downstreamHeaders.set(mutated.getRequest().getHeaders());
			return readBody(mutated, downstreamBody);
		};

		modifyBodyFilter().filter(postExchange(), chain).block();

		assertThat(downstreamHeaders.get().getFirst("X-Added")).isEqualTo("added");
		assertThat(downstreamHeaders.get().getFirst("X-Original")).isEqualTo("original");
		// the rewritten body must survive the mutation
		assertThat(downstreamBody.get()).isEqualTo("REWRITTEN");
	}

	@Test
	public void headersAddedByMutatingTheExchangeAreVisibleDownstream() {
		AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();
		AtomicReference<String> downstreamBody = new AtomicReference<>();
		GatewayFilterChain chain = exchange -> {
			ServerWebExchange mutated = exchange.mutate().request(r -> r.header("X-Added", "added")).build();
			downstreamHeaders.set(mutated.getRequest().getHeaders());
			return readBody(mutated, downstreamBody);
		};

		modifyBodyFilter().filter(postExchange(), chain).block();

		assertThat(downstreamHeaders.get().getFirst("X-Added")).isEqualTo("added");
		assertThat(downstreamBody.get()).isEqualTo("REWRITTEN");
	}

	@Test
	public void headersAreCopiedSoInPlaceChangesAreNotVisibleDownstream() {
		AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();
		AtomicReference<String> downstreamBody = new AtomicReference<>();
		GatewayFilterChain chain = exchange -> {
			// the decorator returns a fresh copy on every call, so this write is lost
			exchange.getRequest().getHeaders().set("X-InPlace", "ignored");
			downstreamHeaders.set(exchange.getRequest().getHeaders());
			return readBody(exchange, downstreamBody);
		};

		modifyBodyFilter().filter(postExchange(), chain).block();

		assertThat(downstreamBody.get()).isEqualTo("REWRITTEN");
		assertThat(downstreamHeaders.get().containsHeader("X-InPlace")).isFalse();
	}

	@Test
	public void headersSetByTheRewriteFunctionAreNotVisibleDownstream() {
		Config config = new Config();
		config.setContentType(MediaType.TEXT_PLAIN_VALUE);
		config.setRewriteFunction(String.class, String.class, (exchange, body) -> {
			// the exchange returned here is discarded by the filter
			exchange.mutate().request(r -> r.header("X-Rewrite", "ignored")).build();
			return Mono.just("REWRITTEN");
		});

		AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();
		AtomicReference<String> downstreamBody = new AtomicReference<>();
		GatewayFilterChain chain = exchange -> {
			downstreamHeaders.set(exchange.getRequest().getHeaders());
			return readBody(exchange, downstreamBody);
		};

		new ModifyRequestBodyGatewayFilterFactory().apply(config).filter(postExchange(), chain).block();

		assertThat(downstreamBody.get()).isEqualTo("REWRITTEN");
		assertThat(downstreamHeaders.get().containsHeader("X-Rewrite")).isFalse();
	}

	private GatewayFilter modifyBodyFilter() {
		Config config = new Config();
		config.setContentType(MediaType.TEXT_PLAIN_VALUE);
		config.setRewriteFunction(String.class, String.class, (exchange, body) -> Mono.just("REWRITTEN"));
		return new ModifyRequestBodyGatewayFilterFactory().apply(config);
	}

	private Mono<Void> readBody(ServerWebExchange exchange, AtomicReference<String> sink) {
		return DataBufferUtils.join(exchange.getRequest().getBody()).doOnNext(buffer -> {
			sink.set(buffer.toString(StandardCharsets.UTF_8));
			DataBufferUtils.release(buffer);
		}).then();
	}

	private MockServerWebExchange postExchange() {
		return MockServerWebExchange.from(MockServerHttpRequest.post("http://localhost/post")
			.header("X-Original", "original")
			.contentType(MediaType.TEXT_PLAIN)
			.body("body"));
	}

}
