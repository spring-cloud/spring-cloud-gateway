/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import org.springframework.web.reactive.function.server.RequestPredicate;

import static org.springframework.cloud.gateway.handler.predicate.PathRequestPredicateFactory.PATTERN_KEY;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class GatewayRequestPredicates {

	//TODO: add support for AfterRequestPredicateFactory

	//TODO: add support for BeforeRequestPredicateFactory

	//TODO: add support for BetweenRequestPredicateFactory

	//TODO: add support for CookieRequestPredicateFactory

	//TODO: add support for GatewayRequestPredicates

	//TODO: add support for HeaderRequestPredicateFactory

	//TODO: add support for HostRequestPredicateFactory

	//TODO: add support for MethodRequestPredicateFactory

	public static RequestPredicate path(String pattern) {
		return new PathRequestPredicateFactory().apply(tuple().of(PATTERN_KEY, pattern));
	}

	//TODO: add support for PredicateDefinition

	//TODO: add support for QueryRequestPredicateFactory

	//TODO: add support for RemoteAddrRequestPredicateFactory

}
