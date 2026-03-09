package io.micronaut.aop.introduction;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@Singleton
public class MyDataSourceHelper2 {

    private final InjectionPoint<?> injectionPoint;
    private final Qualifier<?> qualifier;

    public MyDataSourceHelper2(InjectionPoint<?> injectionPoint, Qualifier<?> qualifier) {
        this.injectionPoint = injectionPoint;
        this.qualifier = qualifier;
    }

    public String getName() {
        return Qualifiers.findName(qualifier);
    }

    public String getInjectionPointQualifier() {
        return Qualifiers.findName(injectionPoint.getDeclaringBeanQualifier());
    }
}
