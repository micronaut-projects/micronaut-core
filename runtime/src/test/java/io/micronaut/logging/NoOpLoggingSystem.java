package io.micronaut.logging;

import jakarta.inject.Singleton;

@Singleton
public class NoOpLoggingSystem implements LoggingSystem {
    private boolean initialized;

    @Override
    public void setLogLevel(final String name, final LogLevel level) {
        // NoOp
    }

    @Override
    public void refresh() {
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
