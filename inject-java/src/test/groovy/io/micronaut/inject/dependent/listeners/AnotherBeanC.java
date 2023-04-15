package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Bean;

@Bean
public class AnotherBeanC {
    public boolean destroyed = false;
}
