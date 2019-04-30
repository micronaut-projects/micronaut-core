package io.micronaut.docs.factories.nullable;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;

// tag::class[]
@Factory
public class EngineFactory {

    @EachBean(EngineConfiguration.class)
    public Engine buildEngine(EngineConfiguration engineConfiguration) {
        if (engineConfiguration.isEnabled()) {
            return engineConfiguration::getCylinders;
        } else {
            return null;
        }
    }
}
// end::class[]
