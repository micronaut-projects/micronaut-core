package io.micronaut.runtime.event

import io.micronaut.context.event.ApplicationEvent

class MyEvent extends ApplicationEvent {

    MyEvent(Object source) {
        super(source)
    }
}
