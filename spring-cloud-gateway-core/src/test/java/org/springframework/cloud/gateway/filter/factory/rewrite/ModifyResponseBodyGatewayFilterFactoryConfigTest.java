/*
 * Copyright 2013-2019 the original author or authors.
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

import org.junit.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class ModifyResponseBodyGatewayFilterFactoryConfigTest {

	@Test
	public void shouldSetRewriteFunction() {
		RewriteFunction rewriteFunction = (exchange, response) -> Mono.just(response);
		EmptyBodyRewriteFunction<String> emptyBodyRewriteFunction = (exchange) -> Mono.just("");
		ModifyResponseBodyGatewayFilterFactory.Config config = new ModifyResponseBodyGatewayFilterFactory.Config();
		config.setRewriteFunction(String.class, String.class, rewriteFunction).setEmptyBodyRewriteFunction(emptyBodyRewriteFunction);

		assertThat(config.getRewriteFunction()).isEqualTo(rewriteFunction);
		assertThat(config.getEmptyBodyRewriteFunction()).isEqualTo(emptyBodyRewriteFunction);
		assertThat(config.getInClass()).isEqualTo(String.class);
		assertThat(config.getOutClass()).isEqualTo(String.class);
	}
}
