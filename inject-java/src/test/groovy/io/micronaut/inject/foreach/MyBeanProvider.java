package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachBean;

import javax.inject.Provider;

@EachBean(MyConfiguration.class)
public class MyBeanProvider {

    final Provider<MyConfiguration> configuration;

    MyBeanProvider(Provider<MyConfiguration> configuration) {
        this.configuration = configuration;
    }

    public MyConfiguration getConfiguration() {
        return configuration.get();
    }
}

