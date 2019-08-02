package io.micronaut.docs.config.properties;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class EngineImpl implements Engine {
    private final EngineConfig config;

    public EngineImpl(EngineConfig config) {// <1>
        this.config = config;
    }

    @Override
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
// end::class[]