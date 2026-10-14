package io.micronaut.dev;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.reload.ReloadCompletedEvent;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A reload listener that asks for another reload, on the reload thread that runs it.
 */
@Singleton
@Requires(property = "spec.name", value = "DevRuntimeLifecycleTest.reentrant")
public class ReentrantReloadListener implements ApplicationEventListener<ReloadCompletedEvent> {
    public static final AtomicReference<Throwable> OUTCOME = new AtomicReference<>();

    private final DevRuntime runtime;

    public ReentrantReloadListener(DevRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void onApplicationEvent(ReloadCompletedEvent event) {
        try {
            runtime.restart();
            OUTCOME.set(new AssertionError("the restart was not refused"));
        } catch (IllegalStateException e) {
            OUTCOME.set(e);
        }
    }
}
