package io.micronaut.docs.config.mapFormat;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

// tag::class[]
@Singleton
public class EngineImpl implements Engine {
    @Override
    public Map getSensors() {
        return config.getSensors();
    }

    public String start() {
        return "Engine Starting V" + getConfig().getCylinders() + " [sensors=" + getSensors().size() + "]";
    }

    public EngineConfig getConfig() {
        return config;
    }

    public void setConfig(EngineConfig config) {
        this.config = config;
    }

    @Inject
    private EngineConfig config;
}
// end::class[]