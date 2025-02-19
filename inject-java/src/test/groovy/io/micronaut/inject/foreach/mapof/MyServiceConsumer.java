package io.micronaut.inject.foreach.mapof;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;

@Requires(property = "spec.name", value = "MapOfNameSpec")
@Singleton
public class MyServiceConsumer {

    private final Map<String, MyService> myServices;

    public MyServiceConsumer(Map<String, MyService> myServices) {
        this.myServices = myServices;
    }

    public Map<String, MyService> getMyServices() {
        return myServices;
    }
}
