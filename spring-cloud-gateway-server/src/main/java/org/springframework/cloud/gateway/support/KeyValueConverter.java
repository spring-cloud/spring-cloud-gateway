package org.springframework.cloud.gateway.support;


import org.springframework.cloud.gateway.filter.factory.AddRequestHeadersIfNotPresentGatewayFilterFactory.KeyValue;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

public class KeyValueConverter implements Converter<String, KeyValue> {

	private static final String INVALID_CONFIGURATION_MESSAGE = "Invalid configuration, expected format is: 'key:value'";

	@Override
	public KeyValue convert(String source) throws IllegalArgumentException {
		try {
			String[] split = source.split(":");
			if (source.contains(":") && StringUtils.hasText(split[0])) {
				return new KeyValue(split[0], split.length == 1 ? "" : split[1]);
			}
			throw new IllegalArgumentException(INVALID_CONFIGURATION_MESSAGE);
		}
		catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException(INVALID_CONFIGURATION_MESSAGE);
		}
	}

}
