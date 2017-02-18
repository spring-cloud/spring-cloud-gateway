package org.springframework.cloud.gateway.support;

import org.springframework.context.ApplicationEvent;

/**
 * @author Spencer Gibb
 */
public class RefreshRoutesEvent extends ApplicationEvent {

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public RefreshRoutesEvent(Object source) {
        super(source);
    }
}
