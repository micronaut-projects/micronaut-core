package io.micronaut.docs.config.mapFormat

import javax.inject.Inject
import javax.inject.Singleton

// tag::class[]
@Singleton
class EngineImpl : Engine {
    override val sensors: Map<*, *>?
        get() = config!!.sensors

    @Inject
    var config: EngineConfig? = null

    override fun start(): String {
        return "Engine Starting V${config!!.cylinders} [sensors=${sensors!!.size}]"
    }
}
// end::class[]