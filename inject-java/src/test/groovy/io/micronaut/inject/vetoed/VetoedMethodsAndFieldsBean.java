package io.micronaut.inject.vetoed;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Vetoed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class VetoedMethodsAndFieldsBean {

    @Inject
    @Vetoed
    public BeanContext fieldInjection;
    public BeanContext methodInjection;

    @Vetoed
    @Inject
    public void setMethodInjection(BeanContext methodInjection) {
        this.methodInjection = methodInjection;
    }

}
