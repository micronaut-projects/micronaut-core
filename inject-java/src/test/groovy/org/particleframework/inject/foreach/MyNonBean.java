package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.EachBean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.EachProperty;

@Factory
public class MyNonBean {

    @EachBean(MyConfiguration.class)
    public NonBeanClass nonBeanClass(MyConfiguration myConfiguration) {
        return new NonBeanClass(myConfiguration.getPort());
    }

}
