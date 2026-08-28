package io.micronaut.aop.bytebuddy;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

@Singleton
public class ByteBuddyStacktraceVerifier implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        boolean found = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("$ByteBuddyProxy")) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("ByteBuddy not present in stack trace");
        }
        return context.proceed();
    }
}
