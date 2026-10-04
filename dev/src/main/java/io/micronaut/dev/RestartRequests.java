/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.dev.http.DevServerSockets;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The HTTP requests across a batch and the restart it may make: a request that arrives during a batch waits for it,
 * those in flight on a stopping generation finish on it, and the servers' listening sockets stay bound from one
 * generation to the next, so that a connection made during a restart waits for the next generation instead of being
 * refused.
 *
 * <p>Only the runtime's reload thread starts, finishes and restarts batches; any thread may wait for admission.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class RestartRequests {

    // the runtime's logger: these are the runtime's messages
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final Duration SERVER_START_WAIT = Duration.ofSeconds(10);

    private final Duration drainTimeout;
    private final @Nullable DevServerSockets serverSockets;
    /**
     * Completes when the requests the gate holds may proceed: when the batch is done, or when a restart drains the
     * generation they arrived on, which serves them before it stops.
     */
    private volatile CompletableFuture<Void> admitted = CompletableFuture.completedFuture(null);

    /**
     * @param manifest The manifest
     * @param retainSockets Whether the listening sockets are kept bound across generations
     */
    RestartRequests(DevManifest manifest, boolean retainSockets) {
        this.drainTimeout = manifest.requestDrainTimeout();
        this.serverSockets = retainSockets ? new DevServerSockets() : null;
    }

    /**
     * @return The future that completes when a request that arrived during a batch may proceed
     */
    CompletableFuture<Void> whenAdmitted() {
        return admitted;
    }

    /**
     * @return The retained listening sockets, if any
     */
    Optional<DevServerSockets> serverSockets() {
        return Optional.ofNullable(serverSockets);
    }

    /**
     * A batch is taken: requests wait for it, held on open connections by the gate filter, while the servers stop
     * accepting new connections, which wait in the backlog.
     *
     * @return The admission of this batch, which {@link #batchDone(CompletableFuture)} completes
     */
    CompletableFuture<Void> batchStarted() {
        CompletableFuture<Void> admission = new CompletableFuture<>();
        admitted = admission;
        DevServerSockets sockets = serverSockets;
        if (sockets != null) {
            sockets.pause();
        }
        return admission;
    }

    /**
     * The batch is done: the servers accept again, and the requests it held proceed.
     *
     * @param admission The admission {@link #batchStarted()} returned
     */
    void batchDone(CompletableFuture<Void> admission) {
        DevServerSockets sockets = serverSockets;
        if (sockets != null) {
            sockets.resume();
        }
        admission.complete(null);
    }

    /**
     * A restart is about to stop a running generation: the requests held during the batch, and those in flight,
     * finish on the generation they arrived on before it stops; the servers stop accepting, and new connections wait
     * for the next generation.
     *
     * @param old The generation that stops
     * @param generation Its number, for the log
     */
    void beforeStop(ApplicationContext old, int generation) {
        admitted.complete(null);
        drain(old, generation);
    }

    /**
     * The next generation failed to start: no generation runs until the next batch, so a request is answered at once
     * rather than left waiting.
     *
     * @param failure Why it failed
     */
    void startFailed(RuntimeException failure) {
        DevServerSockets sockets = serverSockets;
        if (sockets != null) {
            sockets.serveUnavailable(failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
        }
    }

    /**
     * A generation started: its server binds a moment after its context starts, and until then a request waits in the
     * backlog for it rather than being told the application is not running. Once its servers accept, a socket none of
     * them claimed belongs to a listener the configuration dropped, and is released.
     *
     * @param fresh The generation
     */
    void started(ApplicationContext fresh) {
        DevServerSockets sockets = serverSockets;
        if (sockets != null) {
            sockets.stopServingUnavailable();
            if (sockets.isBound()) {
                awaitServers(fresh);
                sockets.releaseUnclaimed();
            }
        }
    }

    /**
     * Closes the retained sockets, once the last generation stopped.
     */
    void close() {
        DevServerSockets sockets = serverSockets;
        if (sockets != null) {
            sockets.close();
        }
    }

    /**
     * Shuts the HTTP servers of a generation down gracefully before the context stops: they stop accepting, close their
     * idle connections, and let every request in flight finish, within {@link DevManifest#requestDrainTimeout()}. A
     * request that would otherwise still run while the context destroys its beans fails half way through.
     */
    private void drain(ApplicationContext context, int generation) {
        List<CompletableFuture<?>> draining = new ArrayList<>();
        for (BeanRegistration<EmbeddedServer> registration : context.getActiveBeanRegistrations(EmbeddedServer.class)) {
            if (registration.getBean() instanceof GracefulShutdownCapable server && registration.getBean().isRunning()) {
                try {
                    draining.add(server.shutdownGracefully().toCompletableFuture());
                } catch (RuntimeException e) {
                    LOG.debug("Cannot drain {}", server, e);
                }
            }
        }
        if (draining.isEmpty()) {
            return;
        }
        Duration timeout = drainTimeout;
        try {
            CompletableFuture.allOf(draining.toArray(CompletableFuture[]::new)).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("Requests or connections were still open on generation {} after {} ms; it stops anyway ({})",
                generation, timeout.toMillis(), DevManifest.REQUESTS_DRAIN_TIMEOUT);
        } catch (ExecutionException e) {
            LOG.debug("Draining generation {} failed", generation, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits, briefly, for the HTTP servers of a generation that just started: the application starts them after its
     * context, on its own thread.
     */
    private static void awaitServers(ApplicationContext generation) {
        long deadline = System.nanoTime() + SERVER_START_WAIT.toNanos();
        while (System.nanoTime() < deadline && generation.isRunning()) {
            Collection<BeanRegistration<EmbeddedServer>> servers = generation.getActiveBeanRegistrations(EmbeddedServer.class);
            // every server created so far: one that starts later than the first must not lose the socket it is about to claim
            if (!servers.isEmpty() && servers.stream().allMatch(registration -> registration.getBean().isRunning())) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
