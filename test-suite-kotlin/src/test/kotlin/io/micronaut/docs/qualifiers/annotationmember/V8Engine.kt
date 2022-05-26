package io.micronaut.docs.qualifiers.annotationmember

import jakarta.inject.Singleton

// tag::class[]
@Singleton
@Cylinders(value = 8, description = "8-cylinder V8 engine") // <1>
class V8Engine : Engine { // <2>
    override val cylinders: Int
        get() = 8

    override fun start(): String {
        return "Starting V8"
    }
}
// end::class[]
