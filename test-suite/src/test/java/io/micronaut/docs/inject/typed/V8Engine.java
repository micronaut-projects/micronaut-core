package io.micronaut.docs.inject.typed;

import io.micronaut.context.annotation.Bean;

import jakarta.inject.Singleton;

// tag::class[]
@Singleton
@Bean(typed = Engine.class) // <1>
public class V8Engine implements Engine {  // <2>
    @Override
    public String start() {
        return "Starting V8";
    }

    @Override
    public int getCylinders() {
        return 8;
    }
}
// end::class[]
