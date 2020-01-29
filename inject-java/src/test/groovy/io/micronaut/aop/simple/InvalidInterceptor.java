package io.micronaut.aop.simple;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;

import javax.inject.Singleton;

@Singleton
public class InvalidInterceptor implements Interceptor {

    @Override
    public Object intercept(InvocationContext context) {
        context.getParameters().put("test", MutableArgumentValue.create(Argument.STRING, "value"));
        return context.proceed();
    }
}
