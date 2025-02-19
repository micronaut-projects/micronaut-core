package io.micronaut.inject.context;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.ContextConfigurer;

@ContextConfigurer
public class TestContextConfigurer
    implements ApplicationContextConfigurer {
    @Override
    public void configure(ApplicationContext applicationContext) {
        applicationContext.registerBeanDefinition(
            RuntimeBeanDefinition.of(new Foo())
        );
    }
}

class Foo {}
