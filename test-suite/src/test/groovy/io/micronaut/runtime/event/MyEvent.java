package io.micronaut.runtime.event;

import io.micronaut.context.event.ApplicationEvent;

public class MyEvent extends ApplicationEvent  {

    public MyEvent(Object source) {
        super(source);
    }
}
