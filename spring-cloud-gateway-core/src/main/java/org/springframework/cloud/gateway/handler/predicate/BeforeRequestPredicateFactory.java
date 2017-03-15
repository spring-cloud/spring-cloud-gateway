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

import java.time.ZonedDateTime;

import org.springframework.tuple.Tuple;
import org.springframework.web.reactive.function.server.RequestPredicate;

import static org.springframework.cloud.gateway.handler.predicate.BetweenRequestPredicateFactory.parseZonedDateTime;

/**
 * @author Spencer Gibb
 */
public class BeforeRequestPredicateFactory implements RequestPredicateFactory {

	@Override
	public RequestPredicate apply(Tuple args) {
		validate(1, args);
		final ZonedDateTime dateTime = parseZonedDateTime(args.getString(0));

		return request -> {
			final ZonedDateTime now = ZonedDateTime.now();
			return now.isBefore(dateTime);
		};
	}

}
