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

package org.springframework.cloud.gateway.test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Base64Utils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import org.synchronoss.cloud.nio.multipart.AbstractNioMultipartListener;
import org.synchronoss.cloud.nio.multipart.Multipart;
import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.NioMultipartParser;
import org.synchronoss.cloud.nio.stream.storage.StreamStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class TestUtils {
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getMap(Map response, String key) {
		assertThat(response).containsKey(key).isInstanceOf(Map.class);
		return (Map<String, Object>) response.get(key);
	}

	public static void assertStatus(ClientResponse response, HttpStatus status) {
		HttpStatus statusCode = response.statusCode();
		assertThat(statusCode).isEqualTo(status);
	}

	public static HashMap<String, Object> parseMultipart(ServerWebExchange exchange, @RequestBody(required = false) String body) {
		HashMap<String, Object> files = new HashMap<>();

		ServerHttpRequest request = exchange.getRequest();
		HttpHeaders headers = request.getHeaders();
		MediaType contentType = headers.getContentType();
		String charSet = (contentType.getCharset() == null) ? null : contentType.getCharset().toString();
		MultipartContext context = new MultipartContext(contentType.toString(),
				(int)headers.getContentLength(), charSet);
		AbstractNioMultipartListener listener = new AbstractNioMultipartListener() {
			@Override
			public void onPartFinished(StreamStorage streamStorage, Map<String, List<String>> headersFromPart) {
				String contentDisposition = headersFromPart.get("content-disposition").get(0);
				String[] tokens = StringUtils.tokenizeToStringArray(contentDisposition, ";");
				String[] nameTokens = StringUtils.tokenizeToStringArray(tokens[1], "=");
				String name = StringUtils.deleteAny(nameTokens[1], "\"");
				String contentType = headersFromPart.get("content-type").get(0);
				ByteArrayInputStream in = (ByteArrayInputStream) streamStorage.getInputStream();
				try {
					String data = Base64Utils.encodeToString(StreamUtils.copyToByteArray(in));
					files.put(name, "data:"+contentType+";base64,"+data);
				} catch (IOException e) {
					ReflectionUtils.rethrowRuntimeException(e);
				}
			}
		};
		NioMultipartParser parser = Multipart.multipart(context).forNIO(listener);
		parser.write(body.getBytes(), 0, body.getBytes().length);
		return files;
	}
}
