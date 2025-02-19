package io.micronaut.aop.introduction;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@EachBean(MyDataSource.class)
public class MyDataSourceHelper3 {

    private final InjectionPoint<?> injectionPoint;
    private final Qualifier<?> qualifier;

    public MyDataSourceHelper3(InjectionPoint<?> injectionPoint, Qualifier<?> qualifier) {
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
