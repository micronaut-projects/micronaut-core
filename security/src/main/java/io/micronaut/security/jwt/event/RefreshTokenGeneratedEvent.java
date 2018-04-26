package io.micronaut.security.jwt.event;

import io.micronaut.context.event.ApplicationEvent;

public class RefreshTokenGeneratedEvent extends ApplicationEvent {

    /**
     * Triggered when an refresh token is generated
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public RefreshTokenGeneratedEvent(Object source) {
        super(source);
    }
}
