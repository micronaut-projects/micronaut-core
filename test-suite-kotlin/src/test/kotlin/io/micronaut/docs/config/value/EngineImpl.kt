package io.micronaut.docs.config.value

import io.micronaut.context.annotation.Value

// tag::imports[]
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class EngineImpl : Engine {

    @Value("\${my.engine.cylinders:6}") // <1>
    override var cylinders: Int = 0
        protected set

    override fun start(): String {// <2>
        return "Starting V$cylinders Engine"
    }
}
// end::class[]