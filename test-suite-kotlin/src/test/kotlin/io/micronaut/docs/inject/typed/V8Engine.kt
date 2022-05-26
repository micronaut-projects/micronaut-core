package io.micronaut.docs.inject.typed

import io.micronaut.context.annotation.Bean
import jakarta.inject.Singleton

// tag::class[]
@Singleton
@Bean(typed = [Engine::class]) // <1>
class V8Engine : Engine { // <2>
    override fun start(): String {
        return "Starting V8"
    }

    override val cylinders: Int = 8
}
// end::class[]
