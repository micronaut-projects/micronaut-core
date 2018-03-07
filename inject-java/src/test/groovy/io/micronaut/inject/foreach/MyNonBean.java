package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.EachProperty;

@Factory
public class MyNonBean {

    @EachBean(MyConfiguration.class)
    public NonBeanClass nonBeanClass(MyConfiguration myConfiguration) {
        return new NonBeanClass(myConfiguration.getPort());
    }

}
