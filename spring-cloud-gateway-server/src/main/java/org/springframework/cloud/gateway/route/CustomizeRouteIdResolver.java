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

package org.springframework.cloud.gateway.route;

import java.util.function.Function;

import org.springframework.web.server.ServerWebExchange;

/**
 * Custom routing ID resolution interface
 *
 * If you want to implement your own custom route resolution and adaptation
 * (that is, resolve the route definition ID through the ServerWebExchange object),
 * then you only need to implement the interface and rewrite the apply method,
 * and inject the class into the Spring container.
 *
 * @author: yizhenqiang
 * @date: 2021/4/30 10:11
 */
public interface CustomizeRouteIdResolver extends Function<ServerWebExchange, String> {

    /**
     * The user-defined routing information is parsed through {@link ServerWebExchange}
     * <p>
     * You can use this method again to resolve a routing ID through ServerWebExchange
     * according to your own routing rules.
     * @param serverWebExchange serverWebExchange the web server context.
     * @return Route definition ID resolved according to custom routing rules.
     */
    @Override
    default String apply(ServerWebExchange serverWebExchange) {
        return null;
    }

}
