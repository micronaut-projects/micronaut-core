package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextLifeCycle
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * An embedded server that received every listener: stopping it stops the context.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.server")
class ListenerServer implements ApplicationContextLifeCycle<ListenerServer> {
    final List<ListenerConfiguration> listeners
    final ApplicationContext applicationContext
    private volatile boolean running

    ListenerServer(List<ListenerConfiguration> listeners, ApplicationContext applicationContext) {
        this.listeners = listeners
        this.applicationContext = applicationContext
    }

    @Override
    boolean isRunning() {
        running
    }

    @Override
    ListenerServer start() {
        running = true
        this
    }

    @Override
    ListenerServer stop() {
        if (running) {
            running = false
            ApplicationContextLifeCycle.super.stop()
        }
        this
    }
}
