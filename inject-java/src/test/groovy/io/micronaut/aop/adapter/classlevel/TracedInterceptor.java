package io.micronaut.aop.adapter.classlevel;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {

    static final List<String> INVOCATIONS = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INVOCATIONS.add(context.getMethodName());
        return context.proceed();
    }
}
