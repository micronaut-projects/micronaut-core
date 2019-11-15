package io.micronaut.docs.config.itfce

import javax.inject.Singleton

@Singleton
class Engine(val config: EngineConfig)// <1>
{
    val cylinders: Int
        get() = config.cylinders

    fun start(): String {// <2>
        return  "${config.manufacturer} Engine Starting V${config.cylinders} [rodLength=${config.crankShaft.rodLength ?: 6.0}]"
    }
}
