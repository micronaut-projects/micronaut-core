package io.micronaut.kotlin.processing.aop.factory

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Prototype
import io.micronaut.kotlin.processing.aop.simple.Mutating
import jakarta.inject.Named

@Factory
class ConcreteClassFactory {

    @Prototype
    @Mutating("name")
    @Primary
    fun concreteClass(): ConcreteClass {
        return ConcreteClass(AnotherClass())
    }

    @Prototype
    @Mutating("name")
    @Named("another")
    fun anotherImpl(): ConcreteClass {
        return ConcreteClass(AnotherClass())
    }
}
