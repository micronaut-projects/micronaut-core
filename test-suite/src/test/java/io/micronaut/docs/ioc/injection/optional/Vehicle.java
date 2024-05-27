package io.micronaut.docs.ioc.injection.optional;

//import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Vehicle {
    //@Autowired(required = false) // <1>
    @Inject
    Engine engine = new Engine(6);

    void start() {
        engine.start();
    }

    public Engine getEngine() {
        return engine;
    }
}

record Engine(int cylinders) {
    void start() {
        System.out.println("Vrooom! " + cylinders);
    }
}
