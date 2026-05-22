package io.micronaut.python.annotation.processing.test.introduction.mapped;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class ListenerAdviceInterceptor implements MethodInterceptor<Object, Object> {

    private final Set<Object> receivedMessages = new HashSet<>();

    public Set<Object> getReceivedMessages() {
        return receivedMessages;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getMethodName().equalsIgnoreCase("onApplicationEvent")) {
            receivedMessages.add(context.getParameterValues()[0]);
            return null;
        }
        return context.proceed();
    }
}
