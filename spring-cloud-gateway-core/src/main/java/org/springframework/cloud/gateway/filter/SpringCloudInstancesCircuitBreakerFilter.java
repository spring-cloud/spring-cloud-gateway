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

package org.springframework.cloud.gateway.filter;

import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudInstancesCircuitBreakerFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author liang jianjun
 */
public class SpringCloudInstancesCircuitBreakerFilter implements GlobalFilter, Ordered {

    /**
     * {@link org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter}\
     * LOAD_BALANCER_CLIENT_FILTER_ORDER + 1
     */
    private static final int CIRCUIT_BREAKER_FILTER_ORDER = 10150 + 1;

    private SpringCloudCircuitBreakerFilterFactory springCloudCircuitBreakerFilterFactory;

    public SpringCloudInstancesCircuitBreakerFilter(
            SpringCloudCircuitBreakerFilterFactory springCloudCircuitBreakerFilterFactory) {
        this.springCloudCircuitBreakerFilterFactory = springCloudCircuitBreakerFilterFactory;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        SpringCloudCircuitBreakerFilterFactory.Config config = exchange
                .getAttribute(SpringCloudInstancesCircuitBreakerFilterFactory.CIRCUIT_BREAKER_CONFIG);

        if (config == null || url == null) {
            return chain.filter(exchange);
        }

        config.setName(getName(config, url));
        GatewayFilter gatewayFilter = springCloudCircuitBreakerFilterFactory.apply(config);
        return gatewayFilter.filter(exchange, chain);

    }

    /**
     * @param config config
     * @param url    Gateway request URL.
     * @return the name of the CircuitBreaker
     */
    private String getName(SpringCloudCircuitBreakerFilterFactory.Config config, URI url) {
        return config.getId() + "-" + url.getAuthority();
    }

    @Override
    public int getOrder() {
        return CIRCUIT_BREAKER_FILTER_ORDER;
    }

}
