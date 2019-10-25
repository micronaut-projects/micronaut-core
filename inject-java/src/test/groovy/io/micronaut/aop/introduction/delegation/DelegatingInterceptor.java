package io.micronaut.aop.introduction.delegation;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;

@Singleton
public class DelegatingInterceptor implements MethodInterceptor<Delegating, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Delegating, Object> context) {
        ExecutableMethod<Delegating, Object> executableMethod = context.getExecutableMethod();
        Object[] parameterValues = context.getParameterValues();
        if (executableMethod.getName().equals("test2")) {
            DelegatingIntroduced instance = new DelegatingIntroduced() {
                @Override
                public String test2() {
                    return "good";
                }

                @Override
                public String test() {
                    return "good";
                }
            };
            return executableMethod.invoke(instance, parameterValues);
        } else {
            return executableMethod
                    .invoke(new DelegatingImpl(), parameterValues);
        }

    }
}
