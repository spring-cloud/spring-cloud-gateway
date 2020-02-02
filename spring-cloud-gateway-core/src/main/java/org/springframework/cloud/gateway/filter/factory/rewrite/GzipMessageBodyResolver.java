package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.FileCopyUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
			throw new IllegalStateException("couldn't decode body from gzip",
					e);
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
			throw new IllegalStateException("couldn't encode body to gzip",
					e);
		}
	}
}
