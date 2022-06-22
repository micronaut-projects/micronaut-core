package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Bean;
import io.micronaut.inject.dependent.TestAnn;
import jakarta.inject.Inject;

@Bean
public class AnotherBeanB {
    @Inject
    public AnotherBeanC beanC;
    public boolean destroyed = false;

    @TestAnn
        // dependent interceptor
    void test() {

    }

}
