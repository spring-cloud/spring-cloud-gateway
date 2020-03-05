package org.springframework.cloud.gateway.event;

import org.springframework.context.ApplicationEvent;

/**
 * @author alvin
 */
public class RefreshRoutesResultEvent extends ApplicationEvent {

	private Throwable throwable;

	private RefreshRoutesResult result;

	public RefreshRoutesResultEvent(Object source, Throwable throwable) {
		super(source);
		this.throwable = throwable;
		result = RefreshRoutesResult.ERROR;
	}

	public RefreshRoutesResultEvent(Object source) {
		super(source);
		result = RefreshRoutesResult.SUCCESS;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public enum RefreshRoutesResult {

		SUCCESS, ERROR

	}

	public RefreshRoutesResult getResult() {
		return result;
	}

}
