package io.micronaut.docs.factories.nullable;

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory

// tag::class[]
@Factory
class EngineFactory {

    @EachBean(EngineConfiguration)
    Engine buildEngine(EngineConfiguration engineConfiguration) {
        if (engineConfiguration.enabled) {
            (Engine){ -> engineConfiguration.cylinders }
        } else {
            null
        }
    }
}
// end::class[]
