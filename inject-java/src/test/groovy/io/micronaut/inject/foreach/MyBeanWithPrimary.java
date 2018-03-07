package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;

@EachBean(MyConfigurationWithPrimary.class)
public class MyBeanWithPrimary {
    final MyConfigurationWithPrimary configuration;

    MyBeanWithPrimary(MyConfigurationWithPrimary configuration) {
        this.configuration = configuration;
    }

    public MyConfigurationWithPrimary getConfiguration() {
        return configuration;
    }
}
