package io.micronaut.docs.qualifiers.annotation

import javax.inject.Inject
import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle // tag::constructor[]
@Inject constructor(@V8 val engine: Engine) {

    // end::constructor[]
    fun start(): String {
        return engine.start() // <5>
    }
}
// end::class[]