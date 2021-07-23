package io.micronaut.docs.qualifiers.annotationmember

import jakarta.inject.Singleton

// tag::class[]
@Singleton
@Cylinders(value = 6, description = "6-cylinder V6 engine") // <1>
class V6Engine : Engine { // <2>
    // <2>
    override val cylinders: Int
        get() = 6

    override fun start(): String {
        return "Starting V6"
    }
}
// end::class[]
