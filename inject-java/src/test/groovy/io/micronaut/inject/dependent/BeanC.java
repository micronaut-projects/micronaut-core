package io.micronaut.inject.dependent;

import io.micronaut.context.annotation.Bean;

import jakarta.annotation.PreDestroy;

@Bean
public class BeanC {
    public boolean destroyed = false;

    @PreDestroy
    void destroy() {
        TestData.DESTRUCTION_ORDER.add(BeanC.class.getSimpleName());
        this.destroyed = true;
    }
}
