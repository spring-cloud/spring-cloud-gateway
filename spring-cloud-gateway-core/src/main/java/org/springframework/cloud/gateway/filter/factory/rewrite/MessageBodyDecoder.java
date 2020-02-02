package org.springframework.cloud.gateway.filter.factory.rewrite;

/**
 * Decoder that is used to decode message body in case it's encoding from Content-Encoding
 * header matches encoding returned by {@code encodingType()} call.
 */
public interface MessageBodyDecoder {

	byte[] decode(byte[] encoded);

	String encodingType();
}
