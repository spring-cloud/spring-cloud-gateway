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

import java.util.List;

import org.springframework.http.HttpCookie;
import org.springframework.tuple.Tuple;
import org.springframework.web.reactive.function.server.PublicDefaultServerRequest;
import org.springframework.web.reactive.function.server.RequestPredicate;

/**
 * @author Spencer Gibb
 */
public class CookieRequestPredicateFactory implements RequestPredicateFactory {

	@Override
	public RequestPredicate apply(Tuple args) {
		validate(2, args);
		String name = args.getString(0);
		String regexp = args.getString(1);

		return request -> {
			//TODO: bad cast?
			PublicDefaultServerRequest req = (PublicDefaultServerRequest) request;
			List<HttpCookie> cookies = req.getCookies().get(name);
			for (HttpCookie cookie : cookies) {
				if (cookie.getValue().matches(regexp)) {
					return true;
				}
			}
			return false;
		};
	}
}
