package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.context.annotation.Primary;

import javax.inject.Singleton;

@Factory
public class MyNonBean {

    @ForEach(MyConfiguration.class)
    public NonBeanClass nonBeanClass(MyConfiguration myConfiguration) {
        return new NonBeanClass(myConfiguration.getPort());
    }

}
