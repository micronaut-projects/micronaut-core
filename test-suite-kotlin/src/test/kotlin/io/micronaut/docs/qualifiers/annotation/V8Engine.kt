package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

// tag::class[]
@Singleton
class V8Engine : Engine { // <2>
    override val cylinders = 8

    override fun start(): String {
        return "Starting V8"
    }
}
// end::class[]