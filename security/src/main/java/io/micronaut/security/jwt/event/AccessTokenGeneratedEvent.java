package io.micronaut.security.jwt.event;

import io.micronaut.context.event.ApplicationEvent;

public class AccessTokenGeneratedEvent extends ApplicationEvent {

    /**
     * Triggered when an access token is generated
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public AccessTokenGeneratedEvent(Object source) {
        super(source);
    }
}
