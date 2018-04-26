package io.micronaut.security.event;

import io.micronaut.context.event.ApplicationEvent;

public class LogoutEvent extends ApplicationEvent {
    /**
     * Event triggered when the user successfully logs out.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public LogoutEvent(Object source) {
        super(source);
    }
}
