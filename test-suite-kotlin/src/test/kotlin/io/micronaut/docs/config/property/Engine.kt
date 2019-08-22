package io.micronaut.docs.config.property

// tag::imports[]
import io.micronaut.context.annotation.Property

import javax.inject.Inject
import javax.inject.Singleton

// end::imports[]

// tag::class[]
@Singleton
class Engine {

    @field:Property(name = "my.engine.cylinders") // <1>
    protected var cylinders: Int = 0 // <2>

    @set:Inject
    @setparam:Property(name = "my.engine.manufacturer") // <3>
    var manufacturer: String? = null

    fun cylinders(): Int {
        return cylinders
    }
}
// end::class[]
