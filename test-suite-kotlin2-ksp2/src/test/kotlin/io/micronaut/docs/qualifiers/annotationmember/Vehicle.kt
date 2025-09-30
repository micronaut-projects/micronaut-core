package io.micronaut.docs.qualifiers.annotationmember

import jakarta.inject.Singleton


// tag::constructor[]
@Singleton
class Vehicle(@param:Cylinders(8) val engine: Engine) {
    fun start(): String {
        return engine.start()
    }
}
// end::constructor[]
