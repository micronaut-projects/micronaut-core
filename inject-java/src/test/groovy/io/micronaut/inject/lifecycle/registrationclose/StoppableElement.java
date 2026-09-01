package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.LifeCycle;

public class StoppableElement implements LifeCycle<StoppableElement> {

    private boolean running = true;

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public StoppableElement stop() {
        running = false;
        return this;
    }
}
