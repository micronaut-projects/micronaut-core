package io.micronaut.inject.dependent;

import io.micronaut.context.annotation.Bean;

import jakarta.annotation.PreDestroy;

@Bean
public class BeanE implements InterfaceA {
    public boolean destroyed = false;

    @PreDestroy
    void destroy() {
        TestData.DESTRUCTION_ORDER.add(getClass().getSimpleName());
        this.destroyed = true;
    }
}
