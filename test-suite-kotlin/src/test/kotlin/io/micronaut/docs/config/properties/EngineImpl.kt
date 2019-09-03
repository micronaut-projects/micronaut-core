package io.micronaut.docs.config.properties

import javax.inject.Singleton

// tag::class[]
@Singleton
class EngineImpl(val config: EngineConfig) : Engine {// <1>

    override val cylinders: Int
        get() = config.cylinders

    override fun start(): String {// <2>
        return "${config.manufacturer} Engine Starting V${config.cylinders} [rodLength=${config.crankShaft.rodLength.orElse(6.0)}]"
    }
}
// end::class[]