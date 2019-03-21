/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.gateway.webflux.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.HttpHeaders;

/**
 * Configuration properties for the {@link ProxyExchange} argument handler in
 * <code>@RequestMapping</code> methods.
 * @author Dave Syer
 *
 */
@ConfigurationProperties("spring.cloud.gateway.proxy")
public class ProxyProperties {

    /**
     * Fixed header values that will be added to all downstream requests.
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * A set of sensitive header names that will not be sent downstream by default.
     */
    private Set<String> sensitive = null;

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Set<String> getSensitive() {
        return sensitive;
    }

    public void setSensitive(Set<String> sensitive) {
        this.sensitive = sensitive;
    }

    public HttpHeaders convertHeaders() {
        HttpHeaders headers = new HttpHeaders();
        for (String key : this.headers.keySet()) {
            headers.set(key, this.headers.get(key));
        }
        return headers;
    }

}
