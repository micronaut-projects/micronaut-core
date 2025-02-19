package io.micronaut.aop.introduction;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@EachBean(MyDataSource.class)
public class MyInterceptedInterfaceWrapper {

    private final MyInterceptedInterface myInterceptedInterface;

    public MyInterceptedInterfaceWrapper(@Parameter MyInterceptedInterface myInterceptedInterface) {
        this.myInterceptedInterface = myInterceptedInterface;
    }

    public MyInterceptedInterface getMyInterceptedInterface() {
        return myInterceptedInterface;
    }
}
