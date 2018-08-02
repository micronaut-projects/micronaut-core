package io.micronaut.aop.adapter;

import io.micronaut.aop.Adapter;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;

@javax.inject.Singleton
class Test {

    private boolean invoked = false;

    @Adapter(ApplicationEventListener.class)
    void onStartup(StartupEvent event) {
        invoked = true;
    }

    public boolean isInvoked() {
        return invoked;
    }
}
