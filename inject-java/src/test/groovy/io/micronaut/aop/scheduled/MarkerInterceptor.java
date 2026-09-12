package io.micronaut.aop.scheduled;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationSpec")
public class MarkerInterceptor implements MethodInterceptor<Object, Object> {

    public final List<String> records = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        records.add(context.getDeclaringType().getSimpleName() + "." + context.getMethodName() + ":" + context.isScheduled());
        return context.proceed();
    }
}
