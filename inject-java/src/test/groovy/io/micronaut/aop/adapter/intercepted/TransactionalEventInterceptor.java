package io.micronaut.aop.adapter.intercepted;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InvocationContext;
import jakarta.inject.Singleton;

@Singleton
class TransactionalEventInterceptor implements Interceptor {
    long count = 0;
    @Override
    public Object intercept(InvocationContext context) {
        count++;
        return context.proceed();
    }
}
