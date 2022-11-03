package io.micronaut.inject.vetoed;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Vetoed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Vetoed
@Singleton
public class VetoedBean2 {

    @Inject
    public BeanContext beanContext;

}
