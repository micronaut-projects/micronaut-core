package io.micronaut.docs.config.itfce;

import javax.inject.Singleton;

@Singleton
public class Engine {
    private final EngineConfig config;

    public Engine(EngineConfig config) {// <1>
        this.config = config;
    }

    public int getCylinders() {
        return config.getCylinders();
    }

    public String start() {// <2>
        return getConfig().getManufacturer() + " Engine Starting V" + getConfig().getCylinders() +
                " [rodLength=" + getConfig().getCrankShaft().getRodLength().orElse(6.0d) + "]";
    }

    public final EngineConfig getConfig() {
        return config;
    }
}
