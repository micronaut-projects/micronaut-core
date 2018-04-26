package io.micronaut.security.event;

import io.micronaut.context.event.ApplicationEvent;

public class LoginFailedEvent extends ApplicationEvent {

    /**
     * Event triggered when a login attempted is triggered.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public LoginFailedEvent(Object source) {
        super(source);
    }
}
