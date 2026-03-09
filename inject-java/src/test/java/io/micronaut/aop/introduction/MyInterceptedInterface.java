package io.micronaut.aop.introduction;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@EachBean(MyDataSource.class)
@MyInterceptedPoint
public interface MyInterceptedInterface {

    String getValue();

}
