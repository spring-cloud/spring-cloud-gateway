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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory.Config;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

public class ModifyResponseBodyGatewayFilterFactoryUnitTests {

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setInClass(String.class);
		config.setOutClass(Integer.class);
		config.setNewContentType("mycontenttype");
		GatewayFilter filter = new ModifyResponseBodyGatewayFilterFactory(
				new DefaultServerCodecConfigurer().getReaders(), emptySet(), emptySet())
			.apply(config);
		assertThat(filter.toString()).contains("String").contains("Integer").contains("mycontenttype");
	}

	@Test
	public void emptyBodyPublisherIsPassedToRewriteFunctionAsNull() {
		List<JsonNode> rewrittenBodies = rewriteJsonBody(Flux.empty(), null);

		assertThat(rewrittenBodies).singleElement().isNull();
	}

	@Test
	public void emptyBodyAfterDecompressionIsPassedToRewriteFunctionAsNull() {
		byte[] gzippedEmptyBody = new GzipMessageBodyResolver().encode(buffer(new byte[0]));

		List<JsonNode> rewrittenBodies = rewriteJsonBody(Flux.just(buffer(gzippedEmptyBody)), "gzip");

		assertThat(rewrittenBodies).singleElement().isNull();
	}

	@Test
	public void validJsonBodyIsDecodedBeforeRewriting() {
		List<JsonNode> rewrittenBodies = rewriteJsonBody(Flux.just(buffer("{\"a\":1}".getBytes(UTF_8))), null);

		assertThat(rewrittenBodies).singleElement().extracting(body -> body.get("a").asInt()).isEqualTo(1);
	}

	@Test
	public void malformedNonEmptyJsonBodyStillFailsToDecode() {
		Flux<DataBuffer> truncatedJson = Flux.just(buffer("{\"a\":".getBytes(UTF_8)));

		assertThatThrownBy(() -> rewriteJsonBody(truncatedJson, null)).isInstanceOf(DecodingException.class);
	}

	private List<JsonNode> rewriteJsonBody(Flux<DataBuffer> upstreamBody, String contentEncoding) {
		List<JsonNode> rewrittenBodies = new ArrayList<>();
		Config config = new Config().setRewriteFunction(JsonNode.class, JsonNode.class, (exchange, body) -> {
			rewrittenBodies.add(body);
			return Mono.justOrEmpty(body);
		});
		GzipMessageBodyResolver gzip = new GzipMessageBodyResolver();
		GatewayFilter filter = new ModifyResponseBodyGatewayFilterFactory(
				new DefaultServerCodecConfigurer().getReaders(), Set.of(gzip), Set.of(gzip))
			.apply(config);

		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/resource"));
		exchange.getAttributes().put(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR, MediaType.APPLICATION_JSON_VALUE);
		exchange.getResponse().setStatusCode(HttpStatus.OK);
		if (contentEncoding != null) {
			exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_ENCODING, contentEncoding);
		}

		filter.filter(exchange, filtered -> filtered.getResponse().writeWith(upstreamBody)).block();
		return rewrittenBodies;
	}

	private DataBuffer buffer(byte[] content) {
		return DefaultDataBufferFactory.sharedInstance.wrap(content);
	}

}
