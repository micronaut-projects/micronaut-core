package io.micronaut.docs.context.events.listener;

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent;
import io.micronaut.runtime.event.annotation.EventListener;
// end::imports[]
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class SampleEventListener {
    private int invocationCounter = 0;

    @EventListener
    public void onSampleEvent(SampleEvent event) {
        invocationCounter++;
    }

    public int getInvocationCounter() {
        return invocationCounter;
    }
}
// end::class[]
