package io.micronaut.aop.introduction;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@EachBean(MyDataSource.class)
public class MyDataSourceHelper {

    private final InjectionPoint<?> injectionPoint;
    private final Qualifier<?> qualifier;
    private final MyDataSourceHelper2 helper;
    private final MyDataSourceHelper3 helper3;

    public MyDataSourceHelper(InjectionPoint<?> injectionPoint,
                              Qualifier<?> qualifier,
                              MyDataSourceHelper2 helper2,
                              @Parameter MyDataSourceHelper3 helper3) {
        this.qualifier = qualifier;
        this.helper = helper2;
        this.helper3 = helper3;
        this.injectionPoint = injectionPoint;
    }

    public String getName() {
        return Qualifiers.findName(qualifier);
    }

    public String getInjectionPointQualifier() {
        return Qualifiers.findName(injectionPoint.getDeclaringBeanQualifier());
    }

    public MyDataSourceHelper2 getHelper2() {
        return helper;
    }

    public MyDataSourceHelper3 getHelper3() {
        return helper3;
    }
}
