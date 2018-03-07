package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;

@EachBean(MyConfiguration.class)
public class MyBean {
    final MyConfiguration configuration;

    MyBean(MyConfiguration configuration) {
        this.configuration = configuration;
    }

    public MyConfiguration getConfiguration() {
        return configuration;
    }
}
