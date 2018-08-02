package io.micronaut.runtime.event.annotation;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Async;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class AsyncListener {

    boolean invoked = false;

    @EventListener
    @Async
    CompletableFuture<Boolean> onStartup(StartupEvent event) {
        try {
            Thread.currentThread().sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        invoked = true;
        return CompletableFuture.completedFuture(invoked);
    }

    public boolean isInvoked() {
        return invoked;
    }
}
