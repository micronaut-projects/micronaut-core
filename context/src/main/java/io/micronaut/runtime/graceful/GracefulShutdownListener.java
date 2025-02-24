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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    GracefulShutdownListener(GracefulShutdownManager manager, GracefulShutdownConfiguration config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        long start = 0;
        if (LOG.isDebugEnabled()) {
            start = System.nanoTime();
            LOG.debug("Starting graceful shutdown...");
        }
        Duration gracePeriod = config.getGracePeriod();
        try {
            manager.shutdownGracefully()
                .toCompletableFuture()
                .get(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);

            if (LOG.isDebugEnabled()) {
                long end = System.nanoTime();
                LOG.debug("Graceful shutdown complete in {}ms", TimeUnit.NANOSECONDS.toMillis(end - start));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.warn("Error in graceful shutdown. This is against the GracefulShutdownCapable contract!", e);
        } catch (TimeoutException e) {
            LOG.warn("Timeout hit in graceful shutdown, forcing stop");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
