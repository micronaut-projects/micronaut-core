package io.micronaut.tracing.instrument.scheduling;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.tracing.instrument.util.TracingRunnable;
import io.opentracing.Tracer;

import javax.inject.Singleton;
import java.util.concurrent.ThreadFactory;

@Singleton
public class ThreadFactoryInstrumenter implements BeanCreatedEventListener<ThreadFactory> {
    private final Tracer tracer;

    public ThreadFactoryInstrumenter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ThreadFactory onCreated(BeanCreatedEvent<ThreadFactory> event) {
        ThreadFactory original = event.getBean();
        return r -> original.newThread(new TracingRunnable(r, tracer));
    }
}
