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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * What a launcher does about an application that failed to start: tells whoever started it, and waits for the change
 * that starts it again. What watches for that change runs on daemon threads and the generation's own threads are gone,
 * so without a thread of its own the JVM would exit with status 0, as if the application had finished.
 *
 * @since 5.3.0
 */
@Internal
final class StartFailures {

    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final Duration CHECK_INTERVAL = Duration.ofMillis(200);

    private final CountDownLatch closed;
    /**
     * Whether the last generation failed to start, so that the next batch must launch one whether or not a class changed.
     */
    private volatile boolean failed;
    private @Nullable Thread hold; // guarded by this

    /**
     * @param closed Counted down once the runtime closed, which ends the wait
     */
    StartFailures(CountDownLatch closed) {
        this.closed = closed;
    }

    /**
     * @param cause What a generation's main threw
     * @return Its message, with the message of its root cause when that says more
     */
    static String why(Throwable cause) {
        String why = cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage();
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != cause && root.getMessage() != null && !why.contains(root.getMessage())) {
            why += " (" + root.getMessage() + ")";
        }
        return why;
    }

    /**
     * Reports a generation that does not run, which a change starts again.
     *
     * @param failure Why it does not run
     */
    static void reportStart(RuntimeException failure) {
        report("Development mode: " + failure.getMessage() + "; a change restarts it", failure.getCause());
    }

    /**
     * Reports a main that failed while the servers it started run, as the JVM reports a main that fails with threads left.
     *
     * @param thrown What it threw
     */
    static void reportMain(@Nullable Throwable thrown) {
        report("Development mode: the application's main failed, its servers keep running: " + (thrown == null ? null : thrown.getMessage()), thrown);
    }

    /**
     * Logs a failure, or prints it when nothing would log it, as with no SLF4J provider on the classpath: the launcher
     * goes on watching, and whoever started it would wait for an application that never answers, not knowing why.
     *
     * @param message What failed
     * @param failure The failure
     */
    @SuppressWarnings("java:S106")
    static void report(String message, @Nullable Throwable failure) {
        if (LOG.isErrorEnabled()) {
            LOG.error(message, failure);
            return;
        }
        System.err.println(message);
        if (failure != null) {
            failure.printStackTrace(System.err);
        }
    }

    /**
     * The last generation failed to start, and none runs until the next batch launches one: keeps the JVM alive until
     * one does or the runtime closes.
     */
    synchronized void hold() {
        failed = true;
        if (hold == null) {
            Thread thread = new Thread(this::await, "micronaut-dev-start-failed");
            thread.setDaemon(false);
            // not the loader of a generation, whichever thread got here
            thread.setContextClassLoader(StartFailures.class.getClassLoader());
            hold = thread;
            thread.start();
        }
    }

    /**
     * As {@link #hold()}, and with the given action, unless the generation that failed is no longer the current one:
     * asked under the monitor {@link #released} takes, so that a later generation's success is not overwritten.
     *
     * @param current Whether the generation is still the current one
     * @param action What else marks it failed
     * @return Whether it is still the current one
     */
    synchronized boolean holdIf(BooleanSupplier current, Runnable action) {
        boolean still = current.getAsBoolean();
        if (still) {
            hold();
            action.run();
        }
        return still;
    }

    /**
     * A generation runs, with threads of its own, unless its main just failed: a {@link #hold()} that follows stands.
     *
     * @param stillRuns Whether it still runs, asked under the same monitor as the hold
     */
    synchronized void released(BooleanSupplier stillRuns) {
        if (stillRuns.getAsBoolean()) {
            failed = false;
        }
    }

    /**
     * @return Whether the last generation failed to start
     */
    boolean failed() {
        return failed;
    }

    private void await() {
        try {
            while (!closed.await(CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)) {
                synchronized (this) {
                    if (!failed) {
                        hold = null;
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The application's main failed, or returned, without a context that started: nothing of it runs any more, unlike a
     * start that timed out or was interrupted, whose main may still be running beside the next generation.
     */
    static final class Failed extends IllegalStateException {

        Failed(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
