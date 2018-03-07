package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.EachProperty;

@Factory
public class MyNonBeanWithPrimary {

    @EachBean(MyConfigurationWithPrimary.class)
    public NonBeanClassWithPrimary nonBeanClassWithPrimary(MyConfigurationWithPrimary myConfiguration) {
        return new NonBeanClassWithPrimary(myConfiguration.port);
    }
}
