package io.micronaut.docs.context.events.listener;

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.context.events.SampleEventEmitterBean;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

// tag::class[]
public class SampleEventListenerSpec {

    @Test
    public void testEventListenerIsNotified() {
        try (ApplicationContext context = ApplicationContext.run()) {
            SampleEventEmitterBean emitter = context.getBean(SampleEventEmitterBean.class);
            SampleEventListener listener = context.getBean(SampleEventListener.class);
            assertEquals(0, listener.getInvocationCounter());
            emitter.publishSampleEvent();
            assertEquals(1, listener.getInvocationCounter());
        }
    }

}
// end::class[]
