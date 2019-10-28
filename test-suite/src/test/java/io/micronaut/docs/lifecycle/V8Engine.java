package io.micronaut.docs.lifecycle;

// tag::imports[]
import javax.annotation.PostConstruct; // <1>
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class V8Engine implements Engine {
    private int cylinders = 8;
    private boolean initialized = false; // <2>

    public String start() {
        if(!initialized) {
            throw new IllegalStateException("Engine not initialized!");
        }

        return "Starting V8";
    }

    @Override
    public int getCylinders() {
        return cylinders;
    }

    public boolean isIntialized() {
        return this.initialized;
    }

    @PostConstruct // <3>
    public void initialize() {
        this.initialized = true;
    }
}
// end::class[]