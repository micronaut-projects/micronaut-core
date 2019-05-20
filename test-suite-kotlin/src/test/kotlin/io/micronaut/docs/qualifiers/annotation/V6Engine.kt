package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

// tag::class[]
@Singleton
class V6Engine : Engine { // <2>
    override val cylinders = 6

    override fun start(): String {
        return "Starting V6"
    }
}
// end::class[]