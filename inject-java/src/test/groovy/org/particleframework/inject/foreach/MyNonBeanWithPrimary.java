package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.EachBean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.EachProperty;

@Factory
public class MyNonBeanWithPrimary {

    @EachBean(MyConfigurationWithPrimary.class)
    public NonBeanClassWithPrimary nonBeanClassWithPrimary(MyConfigurationWithPrimary myConfiguration) {
        return new NonBeanClassWithPrimary(myConfiguration.port);
    }
}
