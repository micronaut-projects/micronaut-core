package io.micronaut.aop.factory.mapped;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class TestSingletonInterceptor implements MethodInterceptor<Object, Object> {

    private final Map<ExecutableMethod, Object> computedSingletons = new ConcurrentHashMap<>(30);

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {

        final ExecutableMethod<Object, Object> method = context.getExecutableMethod();
        synchronized (computedSingletons) {
            Object o = computedSingletons.get(method);
            if (o == null) {
                o = context.proceed();
                if (o == null) {
                    throw new BeanContextException("Bean factor method [" + method + "] returned null");
                }
                computedSingletons.put(method, o);
            }
            return o;
        }
    }

}
