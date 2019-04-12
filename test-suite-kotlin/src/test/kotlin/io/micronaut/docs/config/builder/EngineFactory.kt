package io.micronaut.docs.config.builder

import io.micronaut.context.annotation.Factory
import javax.inject.Singleton

@Factory
internal class EngineFactory {
    @Singleton
    fun buildEngine(engineConfig:EngineConfig): EngineImpl {
        return engineConfig.builder.build(engineConfig.crankShaft, engineConfig.sparkPlug)
    }
}