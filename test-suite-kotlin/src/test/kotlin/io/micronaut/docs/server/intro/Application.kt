package io.micronaut.docs.server.intro

// tag::imports[]
import io.micronaut.runtime.Micronaut
// end::imports[]

// tag::class[]
object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.run(Application.javaClass)
    }
}
// end::class[]
