package io.micronaut.docs.context.events.async;

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
// end::imports[]
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class SampleEventListener {

    private int invocationCounter = 0;

    @EventListener
    @Async
    public void onSampleEvent(SampleEvent event) {
        invocationCounter = invocationCounter++;
    }

    public int getInvocationCounter() {
        return invocationCounter;
    }

}
// end::class[]
