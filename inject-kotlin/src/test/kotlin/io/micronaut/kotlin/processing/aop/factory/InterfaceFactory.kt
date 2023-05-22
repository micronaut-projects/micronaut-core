package io.micronaut.kotlin.processing.aop.factory

import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Prototype
import io.micronaut.kotlin.processing.aop.simple.Mutating
import jakarta.inject.Named

@Factory
class InterfaceFactory {

    @Prototype
    @Mutating("name")
    @Primary
    @Executable
    fun interfaceClass(): InterfaceClass<*> {
        return InterfaceImpl()
    }

    @Prototype
    @Mutating("name")
    @Named("another")
    @Executable
    fun anotherImpl(): InterfaceClass<*> {
        return InterfaceImpl()
    }
}
