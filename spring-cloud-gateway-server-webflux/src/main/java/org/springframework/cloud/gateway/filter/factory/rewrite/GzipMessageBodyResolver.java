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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.FileCopyUtils;

public class GzipMessageBodyResolver implements MessageBodyDecoder, MessageBodyEncoder {

	@Override
	public String encodingType() {
		return "gzip";
	}

	@Override
	public byte[] decode(byte[] encoded) {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(encoded);
			GZIPInputStream gis = new GZIPInputStream(bis);
			return FileCopyUtils.copyToByteArray(gis);
		}
		catch (IOException e) {
			throw new IllegalStateException("couldn't decode body from gzip", e);
		}
	}

	@Override
	public byte[] encode(DataBuffer original) {
		try {
			ByteArrayOutputStream bis = new ByteArrayOutputStream();
			GZIPOutputStream gos = new GZIPOutputStream(bis);
			FileCopyUtils.copy(original.asInputStream(), gos);
			return bis.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("couldn't encode body to gzip", e);
		}
	}

}
