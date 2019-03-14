package io.micronaut.docs.inject.intro;

import javax.inject.Singleton;

// tag::class[]
@Singleton// <2>
public class V8Engine implements Engine {
    public String start() {
        return "Starting V8";
    }

    public int getCylinders() {
        return cylinders;
    }

    public void setCylinders(int cylinders) {
        this.cylinders = cylinders;
    }

    private int cylinders = 8;
}
// end::class[]
