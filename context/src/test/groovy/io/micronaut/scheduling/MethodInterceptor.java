package io.micronaut.scheduling;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInterceptedSpec")
public class MethodInterceptor implements io.micronaut.aop.MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}
