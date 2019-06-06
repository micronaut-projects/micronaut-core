package io.micronaut.docs.lifecycle;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

@Singleton
public class V8Engine implements Engine {
    public String start() {
        if (!initialized) throw new IllegalStateException("Engine not initialized!");

        return "Starting V8";
    }

    @PostConstruct
    public void initialize() {
        this.initialized = true;
    }

    public int getCylinders() {
        return cylinders;
    }

    public void setCylinders(int cylinders) {
        this.cylinders = cylinders;
    }

    public boolean getInitialized() {
        return initialized;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    private int cylinders = 8;
    private boolean initialized = false;
}
