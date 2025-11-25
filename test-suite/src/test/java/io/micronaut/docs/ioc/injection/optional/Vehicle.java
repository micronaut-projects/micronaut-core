package io.micronaut.docs.ioc.injection.optional;

//import io.micronaut.context.annotation.Autowired;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "VehicleIocInjectionOptionalSpec")
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
