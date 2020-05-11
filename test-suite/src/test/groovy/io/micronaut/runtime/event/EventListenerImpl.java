package io.micronaut.runtime.event;

import javax.inject.Singleton;

@Singleton
public class EventListenerImpl implements EventListenerContract {

    public boolean called = false;

    @Override
    public void doOnEvent(MyEvent myEvent) {
        called = true;
    }
}
