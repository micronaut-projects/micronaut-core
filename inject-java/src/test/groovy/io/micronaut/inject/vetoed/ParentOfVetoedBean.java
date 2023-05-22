package io.micronaut.inject.vetoed;

import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;

@Singleton
public class ParentOfVetoedBean extends VetoedSuperclassBean {

    public ParentOfVetoedBean(BeanContext beanContext) {
        super(beanContext);
    }

}
