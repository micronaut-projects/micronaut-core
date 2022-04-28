package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Bean;

@Bean
public class AnotherBeanD implements AnotherInterfaceA {
    public boolean destroyed = false;
}
