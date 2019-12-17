package io.micronaut.docs.ioc.beans;

// tag::class[]
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

import javax.annotation.concurrent.Immutable

@Introspected
@Immutable
class Vehicle {

    private final String make
    private final String model
    private final int axels

    Vehicle(String make, String model) {
        this(make, model, 2)
    }

    @Creator // <1>
    Vehicle(String make, String model, int axels) {
        this.make = make
        this.model = model
        this.axels = axels
    }

    String getMake() {
        make
    }

    String getModel() {
        model
    }

    int getAxels() {
        axels
    }
}
// end::class[]
