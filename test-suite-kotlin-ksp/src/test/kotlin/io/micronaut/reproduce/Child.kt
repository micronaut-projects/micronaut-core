package io.micronaut.reproduce

import io.micronaut.context.annotation.Bean
import jakarta.annotation.PostConstruct

@Bean
class Child : Parent() {

    @PostConstruct
    fun init() {
        // If KSP fails to inject, this will throw
        check(context != null) { "context should be injected" }
    }
}


