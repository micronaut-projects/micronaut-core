package io.micronaut.inject.vetoed;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Vetoed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Vetoed
@Singleton
public class VetoedSuperclassBean {

    public final BeanContext constructorInjection;
    @Inject
    public BeanContext fieldInjection;
    public BeanContext methodInjection;

    public VetoedSuperclassBean(BeanContext constructorInjection) {
        this.constructorInjection = constructorInjection;
    }

    @Inject
    public void setMethodInjection(BeanContext methodInjection) {
        this.methodInjection = methodInjection;
    }
}
