package io.micronaut.reproduce;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
public class Parent {
    @Inject ApplicationContext context;

    public ApplicationContext getContext() {
        return context;
    }
}

