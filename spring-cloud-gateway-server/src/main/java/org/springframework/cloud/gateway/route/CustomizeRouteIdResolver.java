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
