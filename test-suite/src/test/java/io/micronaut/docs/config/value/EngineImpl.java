package io.micronaut.docs.config.value;

import io.micronaut.context.annotation.Value;

// tag::imports[]
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class EngineImpl implements Engine {

    @Value("${my.engine.cylinders:6}") // <1>
    protected int cylinders;

    @Override
    public int getCylinders() {
        return this.cylinders;
    }

    public String start() {// <2>
        return "Starting V" + getCylinders() + " Engine";
    }

}
// end::class[]