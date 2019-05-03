package io.micronaut.docs.config.builder

// tag::imports[]
import io.micronaut.context.annotation.Factory

import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Factory
class EngineFactory {

    @Singleton
    EngineImpl buildEngine(EngineConfig engineConfig) {
        engineConfig.builder.build(engineConfig.crankShaft, engineConfig.sparkPlug)
    }
}
// end::class[]