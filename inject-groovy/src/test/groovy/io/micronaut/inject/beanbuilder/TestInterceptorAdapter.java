package io.micronaut.inject.beanbuilder;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ExecutableMethod;

public class TestInterceptorAdapter<T> implements MethodInterceptor<Object, Object> {
    public final BeanRegistration<T> registration;
    private final ExecutableMethod<T, Object> proceedMethod;

    Environment inaccessibleEnv;

    public Environment accessibleEnv;
    private Environment fromMethod;
    private String valFromMethod;

    public TestInterceptorAdapter(BeanRegistration<T> registration, String methodName) {
        this.registration = registration;
        this.proceedMethod = registration.getBeanDefinition()
                                         .getRequiredMethod(methodName, CustomInvocationContext.class);
    }

    public void testMethod(Environment environment, String val) {
        this.fromMethod = environment;
        this.valFromMethod = val;
    }

    public Environment getFromMethod() {
        return fromMethod;
    }

    public String getValFromMethod() {
        return valFromMethod;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return proceedMethod.invoke(
                registration.getBean(),
                (CustomInvocationContext) context::proceed
        );
    }
}
