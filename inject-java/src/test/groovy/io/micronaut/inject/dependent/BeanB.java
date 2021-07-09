package io.micronaut.inject.dependent;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

import jakarta.annotation.PreDestroy;

@Bean
public class BeanB {
    @Inject
    public BeanC beanC;
    public boolean destroyed = false;

    @TestAnn // dependent interceptor
    void test() {

    }

    @PreDestroy
    void destroy() {
        TestData.DESTRUCTION_ORDER.add(BeanB.class.getSimpleName());
        this.destroyed = true;
    }
}
