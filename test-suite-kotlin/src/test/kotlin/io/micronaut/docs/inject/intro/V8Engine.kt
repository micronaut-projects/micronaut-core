package io.micronaut.docs.inject.intro

import javax.inject.Singleton

// tag::class[]
@Singleton// <2>
class V8Engine : Engine {

    override var cylinders = 8
    override fun start(): String {
        return "Starting V8"
    }
}
// end::class[]
