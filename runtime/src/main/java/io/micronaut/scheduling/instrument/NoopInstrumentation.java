package io.micronaut.scheduling.instrument;

import edu.umd.cs.findbugs.annotations.NonNull;

enum NoopInstrumentation implements Instrumentation {

    INSTANCE;

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void close(boolean cleanup) {
        // nothing to do
    }

    @Override
    public @NonNull Instrumentation forceCleanup() {
        return this;
    }
}
