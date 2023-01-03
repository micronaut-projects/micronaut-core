package io.micronaut.inject.configproperties.eachbeaninterceptor;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;

import java.sql.Connection;

@Requires(property = "spec", value = "EachBeanInterceptorSpec")
@EachBean(MyDataSource.class)
@MyTransactionalConnectionAdvice
@Internal
public interface MyTransactionalConnection extends Connection {
}
