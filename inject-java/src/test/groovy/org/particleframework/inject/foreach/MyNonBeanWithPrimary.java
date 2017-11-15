package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.ForEach;

@Factory
public class MyNonBeanWithPrimary {

    @ForEach(MyConfigurationWithPrimary.class)
    public NonBeanClassWithPrimary nonBeanClassWithPrimary(MyConfigurationWithPrimary myConfiguration) {
        return new NonBeanClassWithPrimary(myConfiguration.port);
    }
}
