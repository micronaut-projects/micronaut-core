package io.micronaut.docs.inject.qualifiers.named

import javax.inject.Singleton

// tag::class[]
@Singleton
class V8Engine : Engine {

    override var cylinders: Int = 8

    override fun start(): String {
        return "Starting V8"
    }

}
// end::class[]
