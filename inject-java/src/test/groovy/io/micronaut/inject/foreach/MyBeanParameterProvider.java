package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;

import javax.inject.Provider;

@EachBean(MyConfiguration.class)
public class MyBeanParameterProvider {

    final Provider<MyConfiguration> configuration;

    MyBeanParameterProvider(@Parameter Provider<MyConfiguration> configuration) {
        this.configuration = configuration;
    }

    public MyConfiguration getConfiguration() {
        return configuration.get();
    }

}
