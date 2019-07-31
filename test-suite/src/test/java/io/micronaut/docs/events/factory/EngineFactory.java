package io.micronaut.docs.events.factory;

import io.micronaut.context.annotation.Factory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

// tag::class[]
@Factory
public class EngineFactory {

    private V8Engine engine;
    private double rodLength = 5.7;

    @PostConstruct
    public void initialize() {
        engine = new V8Engine(rodLength); // <2>
    }

    @Singleton
    public Engine v8Engine() {
        return engine;// <3>
    }

    public void setRodLength(double rodLength) {
        this.rodLength = rodLength;
    }
}
// end::class[]