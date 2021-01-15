package org.springframework.cloud.gateway.logging;

import org.apache.commons.logging.Log;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

public class TestAdaptableLoggerObjectProvider implements ObjectProvider<AdaptableLogger> {

	private Log log;

	public TestAdaptableLoggerObjectProvider(Log log) {
		this.log = log;
	}

	@Override
	public AdaptableLogger getObject(Object... args) throws BeansException {
		return new PassthroughLogger(log);
	}

	@Override
	public AdaptableLogger getIfAvailable() throws BeansException {
		return null;
	}

	@Override
	public AdaptableLogger getIfUnique() throws BeansException {
		return null;
	}

	@Override
	public AdaptableLogger getObject() throws BeansException {
		return null;
	}

}
