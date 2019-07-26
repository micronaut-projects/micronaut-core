package io.micronaut.docs.config.properties

import javax.inject.Singleton

@Singleton
class EngineImpl(val config: EngineConfig)// <1>
    : Engine {

    override val cylinders: Int
        get() = config.cylinders

    override fun start(): String {// <2>
        return config.manufacturer + " Engine Starting V" + config.cylinders.toString() + " [rodLength=" + config.crankShaft.rodLength.orElse(6.0).toString() + "]"
    }
}
