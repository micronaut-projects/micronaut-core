package io.micronaut.aop.introduction;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@Prototype
@InterceptorBean(MyInterceptedPoint.class)
public class MyInterceptedIntroducer implements MethodInterceptor<Object, Object> {

    private final InjectionPoint<?> injectionPoint;

    public MyInterceptedIntroducer(InjectionPoint<?> injectionPoint) {
        this.injectionPoint = injectionPoint;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return Qualifiers.findName(injectionPoint.getDeclaringBeanQualifier());
    }
}
