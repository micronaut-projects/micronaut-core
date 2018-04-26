package io.micronaut.security.event;

import io.micronaut.context.event.ApplicationEvent;

public class LoginSuccesfulEvent extends ApplicationEvent {

    /**
     * Event triggered when the user successfully logs in.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public LoginSuccesfulEvent(Object source) {
        super(source);
    }
}
