/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;

import org.springframework.http.codec.HttpMessageReader;

/**
 * Predicate that reads the body and applies a user provided predicate to run on the body.
 * The body is cached in memory so that possible subsequent calls to the predicate do not
 * need to deserialize again.
 */
@Deprecated
public class ReadBodyPredicateFactory extends ReadBodyRoutePredicateFactory {

	public ReadBodyPredicateFactory() {
	}

	public ReadBodyPredicateFactory(List<HttpMessageReader<?>> messageReaders) {
		super(messageReaders);
	}

}
