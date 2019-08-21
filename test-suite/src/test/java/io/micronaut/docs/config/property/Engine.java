package io.micronaut.docs.config.property;

// tag::imports[]
import io.micronaut.context.annotation.Property;

import javax.inject.Inject;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class Engine {

    @Property(name = "my.engine.cylinders") // <1>
    protected int cylinders; // <2>

    private String manufacturer;

    public int getCylinders() {
        return cylinders;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    @Inject
    public void setManufacturer(@Property(name = "my.engine.manufacturer") String manufacturer) { // <3>
        this.manufacturer = manufacturer;
    }

}
// end::class[]
