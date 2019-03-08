package io.micronaut.docs.inject.qualifiers.named

import javax.inject.Singleton

// tag::class[]
@Singleton
class V6Engine : Engine {  // <2>

    override var cylinders: Int = 6

    override fun start(): String {
        return "Starting V6"
    }
}
// end::class[]
