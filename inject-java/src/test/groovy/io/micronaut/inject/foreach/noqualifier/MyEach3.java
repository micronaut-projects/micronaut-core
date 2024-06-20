package io.micronaut.inject.foreach.noqualifier;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec", value = "EachBeanNoQualifierSpec")
@EachBean(MyService.class)
public class MyEach3 {

    private final BeanRegistration<MyService> myServiceBeanReg;
    private final Qualifier<MyService> qualifier;

    public MyEach3(BeanRegistration<MyService> myServiceBeanReg, Qualifier<MyService> qualifier) {
        this.myServiceBeanReg = myServiceBeanReg;
        this.qualifier = qualifier;
    }

    public BeanRegistration<MyService> getMyServiceBeanReg() {
        return myServiceBeanReg;
    }

    public Qualifier<MyService> getQualifier() {
        return qualifier;
    }
}
