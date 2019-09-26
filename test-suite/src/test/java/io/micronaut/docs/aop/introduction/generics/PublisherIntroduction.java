package io.micronaut.docs.aop.introduction.generics;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import java.lang.reflect.Method;
import javax.inject.Singleton;

@Singleton
public final class PublisherIntroduction implements MethodInterceptor<GenericPublisher<?>, Object> {

    @Override
    public Object intercept(final MethodInvocationContext<GenericPublisher<?>, Object> context) {
        final Method method = context.getTargetMethod();
        if (isEqualsMethod(method)) {
            // Only consider equal when proxies are identical.
            return context.getTarget() == context.getParameterValues()[0];

        } else if (isHashCodeMethod(method)) {
            return hashCode();

        } else if (isToStringMethod(method)) {
            return toString();

        } else {
            return context.getParameterValues()[0].getClass().getSimpleName();
        }
    }

    private static boolean isEqualsMethod(final Method method) {
        if ((method == null) || !"equals".equals(method.getName())) {
            return false;
        }
        final Class<?>[] paramTypes = method.getParameterTypes();
        return (paramTypes.length == 1) && (paramTypes[0] == Object.class);
    }

    private static boolean isHashCodeMethod(final Method method) {
        return (method != null) && "hashCode".equals(method.getName()) && (method.getParameterTypes().length == 0);
    }

    private static boolean isToStringMethod(final Method method) {
        return (method != null) && "toString".equals(method.getName()) && (method.getParameterTypes().length == 0);
    }

}
