package io.micronaut.scheduling.marker;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationMarkerSpec")
public class ScheduledRecordingInterceptor implements MethodInterceptor<Object, Object> {

    public final List<Record> records = new CopyOnWriteArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        records.add(new Record(
            context.getTarget().getClass().getSimpleName(),
            context.getMethodName(),
            context.isScheduled(),
            Thread.currentThread().getName()
        ));
        return context.proceed();
    }

    public List<Record> records(String method) {
        return records.stream().filter(r -> r.method().equals(method)).toList();
    }

    public record Record(String target, String method, boolean scheduled, String thread) {
    }
}
