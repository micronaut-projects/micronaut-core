package io.micronaut.docs.config.mapFormat

import javax.inject.Inject
import javax.inject.Singleton

// tag::class[]
@Singleton
class EngineImpl implements Engine {

    @Inject EngineConfig config

    @Override
    Map getSensors() {
        config.sensors
    }

    String start() {
        "Engine Starting V${config.cylinders} [sensors=${sensors.size()}]"
    }
}
// end::class[]