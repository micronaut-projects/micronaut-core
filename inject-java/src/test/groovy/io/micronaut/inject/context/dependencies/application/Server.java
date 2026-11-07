package io.micronaut.inject.context.dependencies.application;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextLifeCycle;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * An embedded server: destroying it stops it, and stopping it stops the context.
 */
@Singleton
@Requires(property = "spec.name", value = "RecreateRunningApplicationSpec")
public class Server implements ApplicationContextLifeCycle<Server> {

    final Codec codec;
    private final ApplicationContext context;
    private volatile boolean running;

    public Server(Codec codec, ApplicationContext context) {
        this.codec = codec;
        this.context = context;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return context;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Server start() {
        running = true;
        return this;
    }

    @Override
    public Server stop() {
        if (running) {
            running = false;
            ApplicationContextLifeCycle.super.stop();
        }
        return this;
    }
}
