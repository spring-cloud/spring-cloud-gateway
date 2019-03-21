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
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.Arrays;

public class DedupeResponseHeaderGatewayFilterFactoryUnitTests {

    private static final String NAME_1 = HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
    private static final String NAME_2 = HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;

    private HttpHeaders headers;
    private DedupeResponseHeaderGatewayFilterFactory.Config config;
    private DedupeResponseHeaderGatewayFilterFactory filter;

    @Before
    public void setUp() {
        headers = Mockito.mock(HttpHeaders.class);
        config = new DedupeResponseHeaderGatewayFilterFactory.Config();
        filter = new DedupeResponseHeaderGatewayFilterFactory();
    }

    @Test
    public void dedupNullName() {
        filter.dedupe(headers, config);
        Mockito.verify(headers, Mockito.never()).get(Mockito.anyString());
        Mockito.verify(headers, Mockito.never()).set(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void dedupNullValues() {
        config.setName(NAME_1);
        Mockito.when(headers.get(NAME_1)).thenReturn(null);
        filter.dedupe(headers, config);
        Mockito.verify(headers).get(NAME_1);
        Mockito.verify(headers, Mockito.never()).set(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void dedupEmptyValues() {
        config.setName(NAME_1);
        Mockito.when(headers.get(NAME_1)).thenReturn(new ArrayList<>());
        filter.dedupe(headers, config);
        Mockito.verify(headers).get(NAME_1);
        Mockito.verify(headers, Mockito.never()).set(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void dedupSingleValue() {
        config.setName(NAME_1);
        Mockito.when(headers.get(NAME_1)).thenReturn(Arrays.asList("1"));
        filter.dedupe(headers, config);
        Mockito.verify(headers).get(NAME_1);
        Mockito.verify(headers, Mockito.never()).set(Mockito.anyString(), Mockito.anyString());
    }


    @Test
    public void dedupMultipleValuesRetainFirst() {
        config.setName(NAME_1 + " " + NAME_2);
        Mockito.when(headers.get(NAME_1)).thenReturn(Arrays.asList("2", "3", "3", "4"));
        Mockito.when(headers.get(NAME_2)).thenReturn(Arrays.asList("true", "false"));
        filter.dedupe(headers, config);
		Mockito.verify(headers).get(NAME_1);
		Mockito.verify(headers).set(NAME_1, "2");
		Mockito.verify(headers).get(NAME_2);
		Mockito.verify(headers).set(NAME_2, "true");
        Mockito.verify(headers, Mockito.times(2)).set(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void dedupMultipleValuesRetainLast() {
        config.setName(NAME_1);
        config.setStrategy(DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_LAST);
        Mockito.when(headers.get(NAME_1)).thenReturn(Arrays.asList("2", "3", "3", "4"));
        filter.dedupe(headers, config);
        Mockito.verify(headers).get(NAME_1);
        Mockito.verify(headers).set(NAME_1, "4");
        Mockito.verify(headers).set(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void dedupMultipleValuesRetainUnique() {
        config.setName(NAME_1);
        config.setStrategy(DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE);
        Mockito.when(headers.get(NAME_1)).thenReturn(Arrays.asList("2", "3", "3", "4"));
        filter.dedupe(headers, config);
        Mockito.verify(headers).get(NAME_1);
        Mockito.verify(headers).put(Mockito.eq(NAME_1), Mockito.eq(Arrays.asList("2", "3", "4")));
        Mockito.verify(headers).put(Mockito.anyString(), Mockito.anyList());
    }
}