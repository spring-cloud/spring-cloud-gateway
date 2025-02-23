/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Lu lu
 */
public class FormFilterTests {

	@Test
	public void sameContentLengthWithOriginalPostFormUrlEncodeTest() throws ServletException, IOException {

		byte[] content = "baz=bam".getBytes();

		MockHttpServletRequest request = MockMvcRequestBuilders.post("http://localhost/post?foo=fooquery")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).content(content).buildRequest(null);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain mockFilterChain = new MockFilterChain();
		FormFilter formFilter = new FormFilter();
		formFilter.doFilter(request, response, mockFilterChain);
		ServletRequest filterRequest = mockFilterChain.getRequest();
		ByteBuffer buffer = ByteBuffer.allocate(1024);

		ReadableByteChannel readableByteChannel = Channels.newChannel(filterRequest.getInputStream());
		IOUtils.read(readableByteChannel, buffer);
		buffer.flip();
		assertThat(content.length).isEqualTo(buffer.remaining());

	}

}
