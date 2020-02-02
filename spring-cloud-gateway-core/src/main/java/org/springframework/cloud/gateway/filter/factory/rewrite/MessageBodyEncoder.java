package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.springframework.core.io.buffer.DataBuffer;

/**
 * Encoder that is used to encode message body in case it's encoding from Content-Encoding
 * header matches encoding returned by {@code encodingType()} call.
 */
public interface MessageBodyEncoder {

	byte[] encode(DataBuffer original);

	String encodingType();
}
