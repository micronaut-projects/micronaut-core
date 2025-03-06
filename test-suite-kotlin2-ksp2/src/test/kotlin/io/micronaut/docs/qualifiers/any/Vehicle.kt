package io.micronaut.docs.qualifiers.any

import io.micronaut.docs.qualifiers.annotationmember.Engine
// tag::imports[]
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Any
import jakarta.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton
class Vehicle(@param:Any val engineProvider: BeanProvider<Engine>) { // <1>
    fun start() {
        engineProvider.ifPresent { it.start() } // <2>
    }
    // tag::startAll[]
    fun startAll() {
        if (engineProvider.isPresent) { // <1>
            engineProvider.forEach { it.start() } // <2>
        }
    } // end::startAll[]
// tag::clazz[]
}
// end::clazz[]
