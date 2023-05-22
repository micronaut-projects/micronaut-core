package io.micronaut.inject.configproperties.eachbeaninterceptor;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InvocationContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;

import javax.sql.DataSource;

@Requires(property = "spec", value = "EachBeanInterceptorSpec")
@Prototype
class MyInterceptor implements Interceptor {
    private final Qualifier<DataSource> qualifier;

    public MyInterceptor(@Nullable Qualifier<DataSource> qualifier) {
        this.qualifier = qualifier;
    }

    @Override
    public Object intercept(InvocationContext context) {
        return qualifier.toString();
    }

}
