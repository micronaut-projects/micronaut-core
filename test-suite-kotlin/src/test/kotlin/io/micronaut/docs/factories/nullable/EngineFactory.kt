package io.micronaut.docs.factories.nullable

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory

// tag::class[]
@Factory
class EngineFactory {

    @EachBean(EngineConfiguration::class)
    fun buildEngine(engineConfiguration: EngineConfiguration): Engine? {
        return if (engineConfiguration.isEnabled) {
            object : Engine {
                override fun getCylinders(): Int {
                    return engineConfiguration.cylinders!!
                }
            }
        } else {
            null
        }
    }
}
// end::class[]
