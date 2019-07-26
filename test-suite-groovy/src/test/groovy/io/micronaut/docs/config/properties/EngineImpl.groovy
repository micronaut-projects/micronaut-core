package io.micronaut.docs.config.properties

import javax.inject.Singleton

// tag::class[]
@Singleton
class EngineImpl implements Engine {
    final EngineConfig config

    EngineImpl(EngineConfig config) { // <1>
        this.config = config
    }

    @Override
    int getCylinders() {
        config.cylinders
    }

    String start() { // <2>
        "${config.manufacturer} Engine Starting V${config.cylinders} [rodLength=${config.crankShaft.rodLength.orElse(6.0d)}]"
    }
}
// end::class[]