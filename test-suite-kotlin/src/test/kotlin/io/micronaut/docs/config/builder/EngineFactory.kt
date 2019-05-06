package io.micronaut.docs.config.builder

// tag::imports[]
import io.micronaut.context.annotation.Factory
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Factory
internal class EngineFactory {

    @Singleton
    fun buildEngine(engineConfig: EngineConfig): EngineImpl {
        return engineConfig.builder.build(engineConfig.crankShaft, engineConfig.sparkPlug)
    }
}
// end::class[]