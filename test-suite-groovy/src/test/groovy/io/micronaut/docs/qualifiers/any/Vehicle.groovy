package io.micronaut.docs.qualifiers.any

import io.micronaut.docs.qualifiers.annotationmember.Engine

// tag::imports[]
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Any
import jakarta.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton
class Vehicle {
    final BeanProvider<Engine> engineProvider

    Vehicle(@Any BeanProvider<Engine> engineProvider) { // <1>
        this.engineProvider = engineProvider
    }
    void start() {
        engineProvider.ifPresent(Engine::start) // <2>
    }
// end::clazz[]

    // tag::startAll[]
    void startAll() {
        if (engineProvider.isPresent()) { // <1>
            engineProvider.each {it.start() } // <2>
        }
    }
    // end::startAll[]

// tag::clazz[]
}
// end::clazz[]
