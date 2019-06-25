package io.micronaut.docs.context.events.async;

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.context.events.SampleEventEmitterBean;
import io.micronaut.docs.context.events.application.SampleEventListener;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// tag::class[]
public class SampleEventListenerSpec {

    @Test
    public void testEventListenerIsNotified() {
        ApplicationContext context = ApplicationContext.run();
        SampleEventEmitterBean emitter = context.getBean(SampleEventEmitterBean.class);
        SampleEventListener listener = context.getBean(SampleEventListener.class);
        assertEquals(0, listener.getInvocationCounter());
        emitter.publishSampleEvent();
        assertEquals(1, listener.getInvocationCounter());
    }

}
// end::class[]
