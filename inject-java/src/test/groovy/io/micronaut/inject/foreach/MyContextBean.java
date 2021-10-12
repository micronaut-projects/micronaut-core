package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;

@EachBean(MyConfiguration.class)
@Context
public class MyContextBean {
    final MyConfiguration configuration;
    MyContextBean(MyConfiguration configuration) {
        this.configuration = configuration;
    }

    public MyConfiguration getConfiguration() {
        return configuration;
    }

}
