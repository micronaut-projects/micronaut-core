package io.micronaut.docs.context.events.async;

// tag::imports[]
import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.context.events.SampleEventEmitterBean;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
// end::imports[]

// tag::class[]
public class SampleEventListenerSpec {

    @Test
    public void testEventListenerIsNotified() {
        try (ApplicationContext context = ApplicationContext.run()) {
            SampleEventEmitterBean emitter = context.getBean(SampleEventEmitterBean.class);
            SampleEventListener listener = context.getBean(SampleEventListener.class);
            assertEquals(0, listener.getInvocationCounter());
            emitter.publishSampleEvent();
            await().atMost(10, SECONDS).until(listener::getInvocationCounter, equalTo(1));
        }
    }

}
// end::class[]
