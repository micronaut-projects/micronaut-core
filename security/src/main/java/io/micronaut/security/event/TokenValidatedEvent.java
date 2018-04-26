package io.micronaut.security.event;

import io.micronaut.context.event.ApplicationEvent;

public class TokenValidatedEvent extends ApplicationEvent {

    /**
     * Triggered when a authentication token is verified.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public TokenValidatedEvent(Object source) {
        super(source);
    }
}
