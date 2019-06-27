package io.micronaut.docs.context.events.application;

// tag::imports[]
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.docs.context.events.SampleEvent;
// end::imports[]
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class SampleEventListener implements ApplicationEventListener<SampleEvent> {
    private int invocationCounter = 0;

    @Override
    public void onApplicationEvent(SampleEvent event) {
        invocationCounter++;
    }

    public int getInvocationCounter() {
        return invocationCounter;
    }
}
