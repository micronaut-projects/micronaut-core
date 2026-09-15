package io.micronaut.aop.adapter.classlevel;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A prototype interceptor so that one instance is created per intercepted target, which is how a
 * Jakarta lifecycle interceptor behaves. The instance count therefore tells us how many targets the
 * class level advice was applied to.
 */
@Prototype
@InterceptorBean(Logged.class)
class LoggedInterceptor implements MethodInterceptor<Object, Object> {

    static final AtomicInteger INSTANCES = new AtomicInteger();
    static final List<String> INVOCATIONS = Collections.synchronizedList(new ArrayList<>());

    LoggedInterceptor() {
        INSTANCES.incrementAndGet();
    }

    static void reset() {
        INSTANCES.set(0);
        INVOCATIONS.clear();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INVOCATIONS.add(context.getTarget().getClass().getSimpleName() + "#" + context.getMethodName());
        return context.proceed();
    }
}
