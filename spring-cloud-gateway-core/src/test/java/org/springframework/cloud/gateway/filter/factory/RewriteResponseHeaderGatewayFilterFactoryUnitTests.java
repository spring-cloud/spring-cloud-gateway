/*
 * Copyright 2013-2018 the original author or authors.
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RewriteResponseHeaderGatewayFilterFactoryUnitTests {

	private RewriteResponseHeaderGatewayFilterFactory filterFactory;

	@Before
	public void setUp() {
		filterFactory = new RewriteResponseHeaderGatewayFilterFactory();
	}

	@Test
	public void testRewriteDollarSlash() {
		Assert.assertEquals("/bar/bar/42", filterFactory.rewrite(
				"/foo/bar", "/foo/(?<segment>.*)", "/$\\{segment}/$\\{segment}/42"));
	}

	@Test
	public void testRewriteMultiple() {
		Assert.assertEquals("/foo/cafe/wat/cafe", filterFactory.rewrite(
				"/foo/bar/wat/bar", "bar", "cafe"));
	}

}
