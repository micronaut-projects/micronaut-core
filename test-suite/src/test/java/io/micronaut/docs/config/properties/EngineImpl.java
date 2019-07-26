package io.micronaut.docs.config.properties;

import javax.inject.Singleton;

@Singleton
public class EngineImpl implements Engine {
    public EngineImpl(EngineConfig config) {// <1>
        this.config = config;
    }

    @Override
    public int getCylinders() {
        return config.getCylinders();
    }

    public String start() {// <2>
        return getConfig().getManufacturer() + " Engine Starting V" + String.valueOf(getConfig().getCylinders()) + " [rodLength=" + String.valueOf(getConfig().getCrankShaft().getRodLength().orElse(6.0d)) + "]";
    }

    public final EngineConfig getConfig() {
        return config;
    }

    private final EngineConfig config;
}
