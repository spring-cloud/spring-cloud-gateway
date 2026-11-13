/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

public abstract class AbstractProxyExchange implements ProxyExchange {

	private final GatewayMvcProperties properties;

	protected AbstractProxyExchange(GatewayMvcProperties properties) {
		this.properties = properties;
	}

	protected int copyResponseBody(ClientHttpResponse clientResponse, InputStream inputStream,
			OutputStream outputStream) throws IOException {
		Objects.requireNonNull(clientResponse, "No ClientResponse specified");
		Objects.requireNonNull(inputStream, "No InputStream specified");
		Objects.requireNonNull(outputStream, "No OutputStream specified");

		int transferredBytes;

		if (isStreamingMediaType(properties.getStreamingMediaTypes(), getContentType(clientResponse))) {
			transferredBytes = copyResponseBodyWithFlushing(inputStream, outputStream);
		}
		else {
			transferredBytes = StreamUtils.copy(inputStream, outputStream);
		}

		return transferredBytes;
	}

	protected void closeResponse(ClientHttpResponse clientResponse, InputStream inputStream,
			@Nullable IOException copyException) {
		Objects.requireNonNull(clientResponse, "No ClientResponse specified");
		Objects.requireNonNull(inputStream, "No InputStream specified");

		if (copyException != null && isStreamingResponse(clientResponse)) {
			// Avoid ClientHttpResponse.close(), which may drain an infinite stream
			// before closing the response.
			try {
				inputStream.close();
			}
			catch (IOException ex) {
				copyException.addSuppressed(ex);
			}
			return;
		}

		clientResponse.close();
	}

	private boolean isStreamingResponse(ClientHttpResponse clientResponse) {
		return isStreamingMediaType(properties.getStreamingMediaTypes(), getContentType(clientResponse));
	}

	/**
	 * Reads the response content type without parsing its parameters. Only the type and
	 * subtype are relevant for streaming detection, and clients may send non-standard
	 * charset values (for example {@code x-user-defined}) that
	 * {@link HttpHeaders#getContentType()} rejects.
	 * @param clientResponse the client response to read the content type from
	 * @return the parsed content type, or {@code null} if the header is missing or cannot
	 * be parsed
	 */
	private static @Nullable MediaType getContentType(ClientHttpResponse clientResponse) {
		String contentType = clientResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		if (contentType == null) {
			return null;
		}
		int parameterIndex = contentType.indexOf(';');
		String mediaType = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
		try {
			return MediaType.parseMediaType(mediaType.trim());
		}
		catch (InvalidMediaTypeException ex) {
			return null;
		}
	}

	private static boolean isStreamingMediaType(List<MediaType> streamingMediaTypes, @Nullable MediaType mediaType) {
		for (var streamingMediaType : streamingMediaTypes) {
			if (streamingMediaType.equalsTypeAndSubtype(mediaType)) {
				return true;
			}
		}
		return false;
	}

	private int copyResponseBodyWithFlushing(InputStream inputStream, OutputStream outputStream) throws IOException {
		int readBytes;
		var totalReadBytes = 0;
		var buffer = new byte[properties.getStreamingBufferSize()];

		while ((readBytes = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, readBytes);
			outputStream.flush();
			if (totalReadBytes < Integer.MAX_VALUE) {
				try {
					totalReadBytes = Math.addExact(totalReadBytes, readBytes);
				}
				catch (ArithmeticException e) {
					totalReadBytes = Integer.MAX_VALUE;
				}
			}
		}

		outputStream.flush();

		return totalReadBytes;
	}

}
