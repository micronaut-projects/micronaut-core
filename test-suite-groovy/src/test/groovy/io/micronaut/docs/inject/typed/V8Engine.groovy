package io.micronaut.docs.inject.typed

import io.micronaut.context.annotation.Bean
import jakarta.inject.Singleton

// tag::class[]
@Singleton
@Bean(typed = Engine) // <1>
class V8Engine implements Engine {  // <2>
    @Override
    String start() { "Starting V8" }

    @Override
    int getCylinders() { 8 }
}
// end::class[]
