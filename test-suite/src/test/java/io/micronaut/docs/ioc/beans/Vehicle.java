package io.micronaut.docs.ioc.beans;

// tag::class[]
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

import javax.annotation.concurrent.Immutable;

@Introspected
@Immutable
public class Vehicle {

    private final String make;
    private final String model;
    private final int axels;

    public Vehicle(String make, String model) {
        this(make, model, 2);
    }

    @Creator // <1>
    public Vehicle(String make, String model, int axels) {
        this.make = make;
        this.model = model;
        this.axels = axels;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public int getAxels() {
        return axels;
    }
}
// end::class[]
