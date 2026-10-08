/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.runtime.graceful;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listener that intercepts {@link ShutdownEvent} to initiate and wait for a graceful shutdown, if
 * configured.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Singleton
@Requires(bean = GracefulShutdownManager.class)
@Requires(property = GracefulShutdownConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Experimental
public final class GracefulShutdownListener implements ApplicationEventListener<ShutdownEvent>, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(GracefulShutdownListener.class);

    private final GracefulShutdownManager manager;
    private final GracefulShutdownConfiguration config;
    private final AtomicReference<Shutdown> shutdown = new AtomicReference<>();

    GracefulShutdownListener(GracefulShutdownManager manager, GracefulShutdownConfiguration config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        shutdownGracefully();
    }

    /**
     * Initiate the graceful shutdown of all {@link GracefulShutdownCapable} beans, unless it has
     * been initiated already, and wait for it to complete, at most until the
     * {@link GracefulShutdownConfiguration#getGracePeriod() grace period} that started with it
     * has passed.
     * <p>The graceful shutdown runs only once. This lets a component that is about to stop
     * abruptly, such as an embedded server whose stop closes the connections it has accepted,
     * drain first, before the {@link ShutdownEvent} that the application context stop publishes
     * afterwards finds nothing left to do.
     *
     * @since 5.2.16
     */
    public void shutdownGracefully() {
        Shutdown current = shutdown.get();
        if (current == null) {
            Shutdown created = new Shutdown(new CompletableFuture<>(), System.nanoTime() + config.getGracePeriod().toNanos());
            current = shutdown.compareAndExchange(null, created);
            if (current == null) {
                current = created;
                start(created.future);
            }
        }
        long remaining = current.deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            // whoever waited for the whole grace period has already reported the timeout
            return;
        }
        try {
            current.future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // reported by start()
        } catch (TimeoutException e) {
            LOG.warn("Timeout hit in graceful shutdown, forcing stop");
        }
    }

    private void start(CompletableFuture<Object> future) {
        long start = System.nanoTime();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting graceful shutdown...");
        }
        CompletionStage<?> stage;
        try {
            stage = manager.shutdownGracefully();
        } catch (RuntimeException e) {
            stage = CompletableFuture.failedStage(e);
        }
        stage.whenComplete((result, error) -> {
            if (error != null) {
                LOG.warn("Error in graceful shutdown. This is against the GracefulShutdownCapable contract!", error);
                future.completeExceptionally(error);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Graceful shutdown complete in {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                }
                future.complete(result);
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * A graceful shutdown that has been initiated.
     *
     * @param future        Completes when all {@link GracefulShutdownCapable} beans have shut down
     * @param deadlineNanos The {@link System#nanoTime()} at which the grace period ends
     */
    private record Shutdown(CompletableFuture<Object> future, long deadlineNanos) {
    }
}
