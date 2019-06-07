package io.micronaut.docs.lifecycle;

// tag::imports[]
import javax.annotation.PostConstruct;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class V8Engine implements Engine {
    private int cylinders = 8;
    private boolean initialized = false; // <2>

    public String start() {
        if (!initialized) throw new IllegalStateException("Engine not initialized!");

        return "Starting V8";
    }

    @PostConstruct // <3>
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
}
// end::class[]