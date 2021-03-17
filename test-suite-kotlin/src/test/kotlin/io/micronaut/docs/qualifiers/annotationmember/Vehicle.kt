package io.micronaut.docs.qualifiers.annotationmember

import javax.inject.Singleton


// tag::constructor[]
@Singleton
class Vehicle(@param:Cylinders(8) val engine: Engine) {
    fun start(): String {
        return engine.start()
    }
}
// end::constructor[]