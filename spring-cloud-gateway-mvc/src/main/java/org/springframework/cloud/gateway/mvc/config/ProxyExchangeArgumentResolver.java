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

package org.springframework.cloud.gateway.mvc.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @author Dave Syer
 *
 */
public class ProxyExchangeArgumentResolver implements HandlerMethodArgumentResolver {

    private RestTemplate rest;

    private HttpHeaders headers;

    private Set<String> sensitive;

    public ProxyExchangeArgumentResolver(RestTemplate builder) {
        this.rest = builder;
    }

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    public void setSensitive(Set<String> sensitive) {
        this.sensitive = sensitive;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ProxyExchange.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
            ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws Exception {
        ProxyExchange<?> proxy = new ProxyExchange<>(rest, webRequest, mavContainer,
                binderFactory, type(parameter));
        proxy.headers(headers);
        if (sensitive != null) {
            proxy.sensitive(sensitive.toArray(new String[0]));
        }
        return proxy;
    }

    private Type type(MethodParameter parameter) {
        Type type = parameter.getGenericParameterType();
        if (type instanceof ParameterizedType) {
            ParameterizedType param = (ParameterizedType) type;
            type = param.getActualTypeArguments()[0];
        }
        return type;
    }

}
