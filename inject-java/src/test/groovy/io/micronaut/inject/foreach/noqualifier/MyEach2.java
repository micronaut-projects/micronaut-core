package io.micronaut.inject.foreach.noqualifier;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec", value = "EachBeanNoQualifierSpec")
@EachBean(MyService.class)
public class MyEach2 {

    private final BeanRegistration<MyService> myServiceBeanReg;

    public MyEach2(BeanRegistration<MyService> myServiceBeanReg) {
        this.myServiceBeanReg = myServiceBeanReg;
    }

    public BeanRegistration<MyService> getMyServiceBeanReg() {
        return myServiceBeanReg;
    }
}
