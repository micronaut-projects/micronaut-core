package io.micronaut.connection.interceptor;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;

import java.sql.Connection;

@Prototype
public class ContextualConnectionInterceptor implements MethodInterceptor<Connection, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Connection, Object> context) {
        // We don't examine result, so just return null
        return null;
    }

}
