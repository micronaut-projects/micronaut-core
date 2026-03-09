package io.micronaut.aop.adapter.intercepted;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

@Singleton
class TransactionalEventInterceptor implements MethodInterceptor {
    long count = 0;
    ExecutableMethod executableMethod;

    @Override
    public Object intercept(MethodInvocationContext context) {
        count++;
        executableMethod = context.getExecutableMethod();
        return context.proceed();
    }

}
