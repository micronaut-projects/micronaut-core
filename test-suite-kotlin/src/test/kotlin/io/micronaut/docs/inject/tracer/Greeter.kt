package io.micronaut.docs.inject.tracer

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@Singleton
@Requires(property = "spec.name", value = "TimingTracerSpec")
class Greeter {
    fun greet() = "Hello"
}
