package io.micronaut.docs.config.itfce

import javax.inject.Singleton

@Singleton
class Engine {
    private final EngineConfig config

    Engine(EngineConfig config) {// <1>
        this.config = config
    }

    int getCylinders() {
        return config.cylinders
    }

    String start() {// <2>
        return "$config.manufacturer Engine Starting V$config.cylinders [rodLength=${config.crankShaft.rodLength.orElse(6.0d)}]"
    }

    final EngineConfig getConfig() {
        return config
    }
}
